package blocklog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.Properties;

/** Settings, seeded with documented defaults on first run the same way anticheat does it. */
public final class Config {

   /**
    * How many entries stay in memory for lookups and rollbacks.
    *
    * Sized for this box, not for a big host: 3 GB of RAM shared with nginx and the bots. At
    * roughly 80 bytes an entry once names are interned, 150k is around 12 MB, which is a
    * rounding error against the heap and still covers days of normal play. Everything past
    * the window is on disk and can be read there.
    */
   public static int memoryEntries = 150000;

   /** Entries older than this are dropped by /bl purge. Nothing purges on its own. */
   public static int purgeDays = 30;

   /** Log craters. Off would mean a creeper hole has no author and looks like a player did it. */
   public static boolean logExplosions = true;

   /** Cap on how many blocks one /bl rollback may change, so a fat-fingered filter cannot eat a world. */
   public static int maxRollback = 100000;

   /** Lines of history per page in /bl lookup and /bl inspect. */
   public static int pageSize = 8;

   private Config() {
   }

   public static void load(File f) {
      if (!f.exists()) {
         write(f);
      }

      Properties p = new Properties();
      InputStream in = null;
      try {
         if (f.exists()) {
            in = new FileInputStream(f);
            p.load(in);
         }
      } catch (IOException e) {
         System.out.println("[blocklog] could not read " + f + ": " + e);
      } finally {
         if (in != null) {
            try { in.close(); } catch (IOException ignored) { }
         }
      }

      memoryEntries = intOf(p, "memory-entries", memoryEntries);
      purgeDays     = intOf(p, "purge-days", purgeDays);
      maxRollback   = intOf(p, "max-rollback-blocks", maxRollback);
      pageSize      = intOf(p, "page-size", pageSize);
      logExplosions = boolOf(p, "log-explosions", logExplosions);

      if (memoryEntries < 1000) {
         memoryEntries = 1000;
      }
      if (pageSize < 1) {
         pageSize = 1;
      }
   }

   private static int intOf(Properties p, String key, int fallback) {
      String v = p.getProperty(key);
      if (v == null) {
         return fallback;
      }
      try {
         return Integer.parseInt(v.trim());
      } catch (NumberFormatException e) {
         System.out.println("[blocklog] " + key + "=" + v + " is not a number, using " + fallback);
         return fallback;
      }
   }

   private static boolean boolOf(Properties p, String key, boolean fallback) {
      String v = p.getProperty(key);
      return v == null ? fallback : Boolean.parseBoolean(v.trim());
   }

   private static void write(File f) {
      PrintWriter w = null;
      try {
         File parent = f.getParentFile();
         if (parent != null) {
            parent.mkdirs();
         }
         w = new PrintWriter(new FileWriter(f));
         w.println("# Block logging. Every place and break is written to world/blocklog.log;");
         w.println("# this file only controls how much of it is kept in memory for querying.");
         w.println();
         w.println("# Entries held in memory. Lookups and rollbacks only see these -- anything");
         w.println("# older is still on disk but has to be read there. ~80 bytes each.");
         w.println("memory-entries=" + memoryEntries);
         w.println();
         w.println("# /bl purge drops entries older than this. Nothing purges automatically.");
         w.println("purge-days=" + purgeDays);
         w.println();
         w.println("# Attribute creeper and TNT craters to what set them off.");
         w.println("log-explosions=" + logExplosions);
         w.println();
         w.println("# Refuse a rollback larger than this. A mistyped filter is the usual cause.");
         w.println("max-rollback-blocks=" + maxRollback);
         w.println();
         w.println("# History lines per page.");
         w.println("page-size=" + pageSize);
      } catch (IOException e) {
         System.out.println("[blocklog] could not write " + f + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }
   }
}
