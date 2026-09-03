package chatbridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * A small file describing the server, for the companion bot's /players and /status commands.
 *
 * Written rather than queried because the bot has no way to ask the server anything -- there is
 * no RCON and no query port on this build. A file the server owns and the bot only reads keeps
 * the coupling one-directional: the bot can never make the server do work, and a bot that is
 * down or wedged cannot affect a tick.
 *
 * Rewritten whole each time, through a temp file and a rename, so the bot never reads a half
 * written list.
 */
public final class Status {

   private static File file;
   private static File temp;
   private static long lastWrite;

   /** Ten seconds is well inside how often anyone asks, and costs nothing between times. */
   private static final long INTERVAL_MILLIS = 10000L;

   private Status() {
   }

   public static void init(File worldDir) {
      file = new File(worldDir, "chatbridge-status.tsv");
      temp = new File(worldDir, "chatbridge-status.tmp");
   }

   public static void maybeWrite(MinecraftServer server) {
      if (file == null || server == null || server.configManager == null) {
         return;
      }
      long now = System.currentTimeMillis();
      if (now - lastWrite < INTERVAL_MILLIS) {
         return;
      }
      lastWrite = now;

      Writer w = null;
      try {
         w = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
         w.write("updated\t" + (now / 1000L) + "\n");

         StringBuilder names = new StringBuilder();
         int count = 0;
         for (int i = 0; i < server.configManager.playerEntities.size(); i++) {
            EntityPlayerMP p = server.configManager.playerEntities.get(i);
            if (names.length() > 0) {
               names.append(",");
            }
            names.append(p.getName());
            count++;
         }
         w.write("online\t" + count + "\n");
         w.write("players\t" + names + "\n");
         w.close();
         w = null;

         if (!temp.renameTo(file)) {
            // renameTo does not replace on every platform; fall back to delete-then-rename.
            file.delete();
            temp.renameTo(file);
         }
      } catch (Throwable t) {
         System.out.println("[chatbridge] could not write the status file: " + t);
      } finally {
         if (w != null) {
            try { w.close(); } catch (Throwable ignored) { }
         }
      }
   }
}
