package blocklog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The block-change log: everything on disk, a bounded window of it in memory.
 *
 * The split matters on this box. It has 3 GB of RAM and the server is already sharing it with
 * nginx and the Discord bots, so an unbounded in-memory log is the kind of thing that works
 * for a month and then OOMs during a build event. Every change is appended to disk, but only
 * the most recent {@link Config#memoryEntries} are held for querying. Lookups and rollbacks
 * therefore cover the recent past -- which is the whole job, since grief gets reported in hours,
 * not months -- and the on-disk file stays as the full record for anything older.
 *
 * Dependency-free tab-separated text, for the same reason as landclaim's ClaimStore: the only
 * JSON library in the jar has an unresolved licence question (see NOTICE.md), and a format you
 * can grep and hand-edit is worth more than a compact one when something goes wrong.
 */
public final class Log {

   private static final ArrayDeque<Entry> RECENT = new ArrayDeque<Entry>();

   /**
    * Player names are repeated on every single entry. Interning them means the deque holds one
    * String per player rather than one per block, which is most of the memory saving here.
    */
   private static final Map<String, String> NAMES = new HashMap<String, String>();

   private static File file;
   private static BufferedWriter out;
   private static int sinceFlush;

   /** Total appended this run, for /bl status. */
   private static long written;

   private Log() {
   }

   public static synchronized String intern(String name) {
      if (name == null) {
         return "?";
      }
      String held = NAMES.get(name);
      if (held == null) {
         NAMES.put(name, name);
         return name;
      }
      return held;
   }

   public static synchronized void load(File dataFile) {
      file = dataFile;
      RECENT.clear();

      if (file.exists()) {
         BufferedReader r = null;
         long lines = 0L;
         try {
            r = new BufferedReader(new FileReader(file));
            String line;
            while ((line = r.readLine()) != null) {
               if (line.length() == 0 || line.charAt(0) == '#') {
                  continue;
               }
               Entry e = Entry.deserialize(line);
               if (e == null) {
                  continue;
               }
               lines++;
               RECENT.addLast(e);
               // Bounded as we read, so loading a large log costs one entry's memory at a time
               // rather than the whole file followed by a trim.
               while (RECENT.size() > Config.memoryEntries) {
                  RECENT.removeFirst();
               }
            }
            System.out.println("[blocklog] read " + lines + " entries, holding the most recent "
                  + RECENT.size());
         } catch (IOException e) {
            System.out.println("[blocklog] could not read " + file + ": " + e);
         } finally {
            if (r != null) {
               try { r.close(); } catch (IOException ignored) { }
            }
         }
      }

      openForAppend();
   }

   private static void openForAppend() {
      try {
         boolean fresh = !file.exists() || file.length() == 0L;
         out = new BufferedWriter(new FileWriter(file, true));
         if (fresh) {
            out.write("# time\tactor\tdim\tx\ty\tz\toldId\toldMeta\tnewId\tnewMeta");
            out.newLine();
            out.flush();
         }
      } catch (IOException e) {
         System.out.println("[blocklog] could not open " + file + " for writing: " + e);
         out = null;
      }
   }

   /**
    * Records one change. Called from the World mixin on the server thread only.
    *
    * Writes are buffered and flushed every 32 entries rather than on each one. A hard crash can
    * therefore lose the last few seconds of log; that is the right trade against a file flush
    * per block placed, and every /bl command flushes first so a listing is never stale.
    */
   public static synchronized void record(long time, String actor, int dim, int x, int y, int z,
                                          int oldId, int oldMeta, int newId, int newMeta) {
      Entry e = new Entry(time, intern(actor), dim, x, y, z, oldId, oldMeta, newId, newMeta);

      RECENT.addLast(e);
      while (RECENT.size() > Config.memoryEntries) {
         RECENT.removeFirst();
      }

      written++;
      if (out == null) {
         return;
      }
      try {
         out.write(e.serialize());
         out.newLine();
         if (++sinceFlush >= 32) {
            out.flush();
            sinceFlush = 0;
         }
      } catch (IOException ex) {
         System.out.println("[blocklog] write failed, logging to disk disabled: " + ex);
         out = null;
      }
   }

   public static synchronized void flush() {
      if (out == null) {
         return;
      }
      try {
         out.flush();
         sinceFlush = 0;
      } catch (IOException ignored) {
      }
   }

   public static synchronized int held() {
      return RECENT.size();
   }

   public static synchronized long writtenThisRun() {
      return written;
   }

   public static synchronized long fileSize() {
      return file == null || !file.exists() ? 0L : file.length();
   }

   /**
    * Every held entry matching the query, oldest first.
    *
    * Returns a copy. Callers walk it while writing blocks back into the world, which re-enters
    * nothing here today but would be an iteration-during-mutation bug the first time it did.
    */
   public static synchronized List<Entry> query(Query q) {
      List<Entry> out2 = new ArrayList<Entry>();
      Iterator<Entry> it = RECENT.iterator();
      while (it.hasNext()) {
         Entry e = it.next();
         if (q.matches(e)) {
            out2.add(e);
         }
      }
      return out2;
   }

   /**
    * Drops entries older than the cutoff from both memory and disk.
    *
    * Rewrites the file through a temp copy and renames, so an interrupted purge leaves the
    * original log intact rather than a half-truncated one.
    */
   public static synchronized long purge(long cutoff) {
      flush();

      long dropped = 0L;
      while (!RECENT.isEmpty() && RECENT.peekFirst().time < cutoff) {
         RECENT.removeFirst();
         dropped++;
      }

      if (file == null || !file.exists()) {
         return dropped;
      }

      File tmp = new File(file.getParentFile(), file.getName() + ".purge");
      BufferedReader r = null;
      BufferedWriter w = null;
      long keptLines = 0L;
      try {
         if (out != null) {
            out.close();
            out = null;
         }
         r = new BufferedReader(new FileReader(file));
         w = new BufferedWriter(new FileWriter(tmp));
         w.write("# time\tactor\tdim\tx\ty\tz\toldId\toldMeta\tnewId\tnewMeta");
         w.newLine();

         String line;
         while ((line = r.readLine()) != null) {
            if (line.length() == 0 || line.charAt(0) == '#') {
               continue;
            }
            Entry e = Entry.deserialize(line);
            if (e == null || e.time < cutoff) {
               continue;
            }
            w.write(line);
            w.newLine();
            keptLines++;
         }
      } catch (IOException e) {
         System.out.println("[blocklog] purge failed, log left as it was: " + e);
         closeQuietly(r, w);
         tmp.delete();
         openForAppend();
         return dropped;
      }

      closeQuietly(r, w);
      if (!file.delete() || !tmp.renameTo(file)) {
         System.out.println("[blocklog] purge wrote " + tmp + " but could not replace " + file);
      }
      openForAppend();
      System.out.println("[blocklog] purged, " + keptLines + " entries remain on disk");
      return dropped;
   }

   private static void closeQuietly(BufferedReader r, BufferedWriter w) {
      if (r != null) {
         try { r.close(); } catch (IOException ignored) { }
      }
      if (w != null) {
         try { w.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void shutdown() {
      flush();
      if (out != null) {
         try { out.close(); } catch (IOException ignored) { }
         out = null;
      }
   }
}
