package basics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the basics commands remember.
 *
 * Three separate things, kept together because they share a save format and a load point:
 * who is AFK (memory only -- it should not survive a restart), who last logged in and when,
 * who is ignoring whom, and unread mail.
 *
 * Tab-separated text for the same reason as the rest of these addons: no JSON library with a
 * settled licence is available, and a file you can fix in a text editor is worth more than a
 * compact one when someone's mail is stuck.
 */
public final class State {

   /** AFK is deliberately not persisted: coming back from a restart still marked away is a bug. */
   private static final Set<String> AFK = new HashSet<String>();

   /** player -> epoch seconds of their last logout. */
   private static final Map<String, Long> SEEN = new LinkedHashMap<String, Long>();

   /** player -> the players they will not receive messages from. */
   private static final Map<String, Set<String>> IGNORES = new HashMap<String, Set<String>>();

   /** recipient -> queued messages. */
   private static final Map<String, List<String>> MAIL = new LinkedHashMap<String, List<String>>();

   private static File seenFile;
   private static File ignoreFile;
   private static File mailFile;

   private State() {
   }

   public static synchronized void load(File dir) {
      seenFile = new File(dir, "basics-seen.tsv");
      ignoreFile = new File(dir, "basics-ignores.tsv");
      mailFile = new File(dir, "basics-mail.tsv");

      SEEN.clear();
      for (String[] row : read(seenFile, 2)) {
         try {
            SEEN.put(row[0], Long.valueOf(Long.parseLong(row[1])));
         } catch (NumberFormatException ignored) {
         }
      }

      IGNORES.clear();
      for (String[] row : read(ignoreFile, 2)) {
         Set<String> set = IGNORES.get(row[0]);
         if (set == null) {
            set = new HashSet<String>();
            IGNORES.put(row[0], set);
         }
         set.add(row[1]);
      }

      MAIL.clear();
      for (String[] row : read(mailFile, 2)) {
         List<String> box = MAIL.get(row[0]);
         if (box == null) {
            box = new ArrayList<String>();
            MAIL.put(row[0], box);
         }
         box.add(row[1]);
      }

      System.out.println("[basics] " + SEEN.size() + " seen, " + IGNORES.size()
            + " ignore lists, " + MAIL.size() + " mailboxes");
   }

   // ---- afk ----

   public static synchronized boolean toggleAfk(String player) {
      String key = player.toLowerCase();
      if (AFK.remove(key)) {
         return false;
      }
      AFK.add(key);
      return true;
   }

   public static synchronized boolean isAfk(String player) {
      return AFK.contains(player.toLowerCase());
   }

   public static synchronized void clearAfk(String player) {
      AFK.remove(player.toLowerCase());
   }

   // ---- seen ----

   public static synchronized void markSeen(String player) {
      SEEN.put(player.toLowerCase(), Long.valueOf(System.currentTimeMillis() / 1000L));
      writeMap(seenFile, SEEN);
   }

   /** Epoch seconds, or 0 if this player has never been recorded. */
   public static synchronized long lastSeen(String player) {
      Long t = SEEN.get(player.toLowerCase());
      return t == null ? 0L : t.longValue();
   }

   // ---- ignores ----

   /** Returns true if now ignoring, false if the ignore was lifted. */
   public static synchronized boolean toggleIgnore(String who, String target) {
      String key = who.toLowerCase();
      String t = target.toLowerCase();
      Set<String> set = IGNORES.get(key);
      if (set == null) {
         set = new HashSet<String>();
         IGNORES.put(key, set);
      }
      boolean added;
      if (set.contains(t)) {
         set.remove(t);
         added = false;
      } else {
         set.add(t);
         added = true;
      }
      if (set.isEmpty()) {
         IGNORES.remove(key);
      }
      writeSets(ignoreFile, IGNORES);
      return added;
   }

   public static synchronized boolean ignores(String who, String target) {
      Set<String> set = IGNORES.get(who.toLowerCase());
      return set != null && set.contains(target.toLowerCase());
   }

   public static synchronized List<String> ignoreList(String who) {
      Set<String> set = IGNORES.get(who.toLowerCase());
      return set == null ? new ArrayList<String>() : new ArrayList<String>(set);
   }

   // ---- mail ----

   public static synchronized void addMail(String to, String line) {
      String key = to.toLowerCase();
      List<String> box = MAIL.get(key);
      if (box == null) {
         box = new ArrayList<String>();
         MAIL.put(key, box);
      }
      // A mailbox is a courtesy, not a message queue. Capped so one person cannot fill the disk.
      if (box.size() >= 30) {
         box.remove(0);
      }
      box.add(line);
      writeLists(mailFile, MAIL);
   }

   public static synchronized List<String> mailFor(String who) {
      List<String> box = MAIL.get(who.toLowerCase());
      return box == null ? new ArrayList<String>() : new ArrayList<String>(box);
   }

   public static synchronized void clearMail(String who) {
      MAIL.remove(who.toLowerCase());
      writeLists(mailFile, MAIL);
   }

   // ---- io ----

   private static List<String[]> read(File f, int columns) {
      List<String[]> rows = new ArrayList<String[]>();
      if (f == null || !f.exists()) {
         return rows;
      }
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null) {
            if (line.length() == 0 || line.charAt(0) == '#') {
               continue;
            }
            // Limit means a message containing a tab survives intact in the last column.
            String[] parts = line.split("\t", columns);
            if (parts.length == columns) {
               rows.add(parts);
            }
         }
      } catch (IOException e) {
         System.out.println("[basics] could not read " + f + ": " + e);
      } finally {
         if (r != null) {
            try { r.close(); } catch (IOException ignored) { }
         }
      }
      return rows;
   }

   private static void writeMap(File f, Map<String, Long> map) {
      BufferedWriter w = open(f);
      if (w == null) {
         return;
      }
      try {
         for (Map.Entry<String, Long> e : map.entrySet()) {
            w.write(e.getKey() + "\t" + e.getValue());
            w.newLine();
         }
      } catch (IOException e) {
         System.out.println("[basics] could not write " + f + ": " + e);
      } finally {
         close(w);
      }
   }

   private static void writeSets(File f, Map<String, Set<String>> map) {
      BufferedWriter w = open(f);
      if (w == null) {
         return;
      }
      try {
         for (Map.Entry<String, Set<String>> e : map.entrySet()) {
            for (String v : e.getValue()) {
               w.write(e.getKey() + "\t" + v);
               w.newLine();
            }
         }
      } catch (IOException e) {
         System.out.println("[basics] could not write " + f + ": " + e);
      } finally {
         close(w);
      }
   }

   private static void writeLists(File f, Map<String, List<String>> map) {
      BufferedWriter w = open(f);
      if (w == null) {
         return;
      }
      try {
         for (Map.Entry<String, List<String>> e : map.entrySet()) {
            for (int i = 0; i < e.getValue().size(); i++) {
               w.write(e.getKey() + "\t" + e.getValue().get(i));
               w.newLine();
            }
         }
      } catch (IOException e) {
         System.out.println("[basics] could not write " + f + ": " + e);
      } finally {
         close(w);
      }
   }

   private static BufferedWriter open(File f) {
      if (f == null) {
         return null;
      }
      try {
         return new BufferedWriter(new FileWriter(f));
      } catch (IOException e) {
         System.out.println("[basics] could not open " + f + ": " + e);
         return null;
      }
   }

   private static void close(BufferedWriter w) {
      try { w.close(); } catch (IOException ignored) { }
   }
}
