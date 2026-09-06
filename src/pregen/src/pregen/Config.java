package pregen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** world/pregen.properties -- written with its defaults on first run. */
public final class Config {

   /**
    * Wall-clock milliseconds per tick to spend generating.
    *
    * A tick is 50 ms. 30 leaves comfortable room on an idle server, which is where this is
    * meant to run; the value only bites when a chunk costs less than the budget, since one
    * chunk is always finished once started.
    */
   public static int budgetMs = 30;

   /**
    * Keep generating while players are online.
    *
    * Off by default. Pregen exists to stop players waiting on the generator, and running it
    * underneath them spends the same CPU they are waiting for -- it would cause the stutter it
    * is there to prevent. Turn it on only if the server is otherwise idle and you are in a
    * hurry.
    */
   public static boolean runWithPlayers = false;

   /** Ticks between progress lines in the log. 1200 = once a minute. */
   public static int reportEveryTicks = 1200;

   private Config() {
   }

   public static void load(File file) {
      Properties p = new Properties();
      if (file.exists()) {
         InputStream in = null;
         try {
            in = new FileInputStream(file);
            p.load(in);
         } catch (Exception e) {
            System.out.println("[pregen] could not read " + file + ": " + e);
         } finally {
            close(in);
         }
      }

      budgetMs = clamp(intOf(p, "budget-ms", budgetMs), 1, 45);
      runWithPlayers = boolOf(p, "run-with-players", runWithPlayers);
      reportEveryTicks = clamp(intOf(p, "report-every-ticks", reportEveryTicks), 100, 72000);

      if (!file.exists()) {
         save(file);
      }
   }

   private static void save(File file) {
      OutputStream out = null;
      try {
         file.getParentFile().mkdirs();
         out = new FileOutputStream(file);
         Properties p = new Properties();
         p.setProperty("budget-ms", String.valueOf(budgetMs));
         p.setProperty("run-with-players", String.valueOf(runWithPlayers));
         p.setProperty("report-every-ticks", String.valueOf(reportEveryTicks));
         p.store(out, "pregen -- generate terrain ahead of players. See /pregen help.");
      } catch (Exception e) {
         System.out.println("[pregen] could not write " + file + ": " + e);
      } finally {
         close(out);
      }
   }

   private static int intOf(Properties p, String key, int fallback) {
      try {
         return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private static boolean boolOf(Properties p, String key, boolean fallback) {
      return Boolean.parseBoolean(p.getProperty(key, String.valueOf(fallback)).trim());
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   private static void close(java.io.Closeable c) {
      if (c != null) {
         try {
            c.close();
         } catch (Exception ignored) {
         }
      }
   }
}
