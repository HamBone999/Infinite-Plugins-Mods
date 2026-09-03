package chatbridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * Discord -> Minecraft, when the companion bot is doing the listening.
 *
 * The bot holds a real gateway connection, so it hears a message the instant it is sent rather
 * than up to poll-seconds later, and it costs no REST requests at all. It hands messages over
 * by dropping one small file per message into a spool directory, which this drains every tick.
 *
 * A directory of files rather than one appended log, because the two processes write and read
 * independently and a shared append-file has no safe hand-off: the reader cannot truncate
 * without racing a write. The bot writes each message to a temp name and renames it into place,
 * and rename is atomic on the same filesystem -- so a file that appears here is always complete,
 * and deleting it after reading can never lose a message that arrived mid-read.
 *
 * The plugin's own REST polling ({@link Inbound}) stays available for anyone running without
 * the bot. The two are independent; with a bot token unset, only this path is active.
 */
public final class Spool {

   private static File dir;

   /** Guards against a runaway producer: no more than this many messages drained per tick. */
   private static final int MAX_PER_TICK = 20;

   private Spool() {
   }

   public static void init(File worldDir) {
      dir = new File(worldDir, "chatbridge-spool");
      if (!dir.exists() && !dir.mkdirs()) {
         System.out.println("[chatbridge] could not create " + dir + "; the bot relay will not work");
         dir = null;
         return;
      }
      // Anything left from a previous run is stale chat; drop it rather than replaying it.
      File[] old = dir.listFiles();
      if (old != null && old.length > 0) {
         for (int i = 0; i < old.length; i++) {
            old[i].delete();
         }
         System.out.println("[chatbridge] cleared " + old.length + " stale spool file(s)");
      }
   }

   public static boolean ready() {
      return dir != null;
   }

   /** Called once a tick from the server thread. */
   public static void drain() {
      if (dir == null) {
         return;
      }
      File[] files = dir.listFiles();
      if (files == null || files.length == 0) {
         return;
      }

      // Oldest first, so a burst of messages arrives in the order it was sent.
      Arrays.sort(files);

      int done = 0;
      for (int i = 0; i < files.length && done < MAX_PER_TICK; i++) {
         File f = files[i];
         if (!f.isFile() || !f.getName().endsWith(".msg")) {
            continue;
         }
         String line = readFirstLine(f);
         f.delete();
         done++;
         if (line == null || line.length() == 0) {
            continue;
         }
         Relay.enqueue(Text.forMinecraft(line, 200));
      }
   }

   /** UTF-8 explicitly: the bot writes UTF-8 and the server's default encoding is not guaranteed. */
   private static String readFirstLine(File f) {
      BufferedReader r = null;
      try {
         r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
         return r.readLine();
      } catch (Throwable t) {
         return null;
      } finally {
         if (r != null) {
            try { r.close(); } catch (Throwable ignored) { }
         }
      }
   }
}
