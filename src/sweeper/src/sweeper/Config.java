package sweeper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;

/**
 * Settings. Everything that removes something a player could still want is conservative by
 * default, and merging -- which removes entities without removing anything a player owns -- is
 * the only part that is aggressive.
 */
public final class Config {

   /** Seconds between sweeps. */
   public static int intervalSeconds = 60;

   /** Merge dropped stacks of the same item within this many blocks of each other. */
   public static boolean merge = true;
   public static double mergeRadius = 2.0;

   /**
    * Remove dropped items older than this. 0 disables it.
    *
    * Off by default. Deleting loot is the one thing here a player can be angry about, and a
    * server that eats the diamonds you died next to is worse than a server that lags.
    */
   public static int itemLifetimeSeconds = 0;

   /** Warn in chat this many seconds before a sweep that will delete items. 0 disables. */
   public static int warnSeconds = 10;

   /** Trim mobs above this count per world. 0 disables. Bosses, named and tamed are never touched. */
   public static int maxMobsPerWorld = 0;

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
         System.out.println("[sweeper] could not read " + f + ": " + e);
      } finally {
         if (in != null) {
            try { in.close(); } catch (IOException ignored) { }
         }
      }

      intervalSeconds     = intOf(p, "interval-seconds", intervalSeconds);
      merge               = boolOf(p, "merge-items", merge);
      mergeRadius         = dblOf(p, "merge-radius", mergeRadius);
      itemLifetimeSeconds = intOf(p, "item-lifetime-seconds", itemLifetimeSeconds);
      warnSeconds         = intOf(p, "warn-seconds", warnSeconds);
      maxMobsPerWorld     = intOf(p, "max-mobs-per-world", maxMobsPerWorld);

      if (intervalSeconds < 10) {
         intervalSeconds = 10;
      }
      if (mergeRadius < 0.5) {
         mergeRadius = 0.5;
      }
   }

   private static int intOf(Properties p, String k, int d) {
      String v = p.getProperty(k);
      if (v == null) return d;
      try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return d; }
   }

   private static double dblOf(Properties p, String k, double d) {
      String v = p.getProperty(k);
      if (v == null) return d;
      try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return d; }
   }

   private static boolean boolOf(Properties p, String k, boolean d) {
      String v = p.getProperty(k);
      return v == null ? d : Boolean.parseBoolean(v.trim());
   }

   private static void write(File f) {
      PrintWriter w = null;
      try {
         File parent = f.getParentFile();
         if (parent != null) parent.mkdirs();
         w = new PrintWriter(new FileWriter(f));
         w.println("# Entity housekeeping. Merging is on; nothing is deleted unless you turn it on.");
         w.println();
         w.println("# Seconds between sweeps. Minimum 10.");
         w.println("interval-seconds=" + intervalSeconds);
         w.println();
         w.println("# Combine dropped stacks of the same item that are close together. This is the");
         w.println("# safe fix: it cuts the entity count without anyone losing an item, so it is on.");
         w.println("merge-items=" + merge);
         w.println("merge-radius=" + mergeRadius);
         w.println();
         w.println("# Delete dropped items older than this many seconds. 0 = never.");
         w.println("# OFF by default on purpose -- a server that eats the loot you died next to is");
         w.println("# worse than a server that lags. 600 (10 minutes) is a reasonable start.");
         w.println("item-lifetime-seconds=" + itemLifetimeSeconds);
         w.println();
         w.println("# Seconds of warning in chat before a sweep that deletes items. 0 = silent.");
         w.println("warn-seconds=" + warnSeconds);
         w.println();
         w.println("# Trim mobs above this count, per world, furthest from a player first.");
         w.println("# 0 = never. Bosses, named mobs and tamed animals are never removed.");
         w.println("max-mobs-per-world=" + maxMobsPerWorld);
      } catch (IOException e) {
         System.out.println("[sweeper] could not write " + f + ": " + e);
      } finally {
         if (w != null) w.close();
      }
   }
}
