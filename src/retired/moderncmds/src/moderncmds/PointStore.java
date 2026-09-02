package moderncmds;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Named points: per-player homes, global warps, and the server spawn. One TSV file, no dependencies. */
public final class PointStore {

   public static final class Point {
      public final double x, y, z;
      public final float yaw, pitch;
      public Point(double x, double y, double z, float yaw, float pitch) {
         this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
      }
   }

   /** key is "home:<player>" or "warp:<name>" or "spawn" */
   private static final Map<String, Point> POINTS = new HashMap<String, Point>();
   private static File file;

   private PointStore() { }

   public static synchronized void load(File f) {
      file = f;
      POINTS.clear();
      if (!file.exists()) return;
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(file));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') continue;
            String[] p = line.split("\t", -1);
            if (p.length < 6) continue;
            try {
               POINTS.put(p[0], new Point(Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                     Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5])));
            } catch (NumberFormatException ignored) { }
         }
         System.out.println("[moderncmds] loaded " + POINTS.size() + " points");
      } catch (IOException e) {
         System.out.println("[moderncmds] could not read " + file + ": " + e);
      } finally {
         if (r != null) try { r.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void save() {
      if (file == null) return;
      BufferedWriter w = null;
      try {
         w = new BufferedWriter(new FileWriter(file));
         w.write("# key\tx\ty\tz\tyaw\tpitch");
         w.newLine();
         for (Map.Entry<String, Point> e : POINTS.entrySet()) {
            Point p = e.getValue();
            w.write(e.getKey() + "\t" + p.x + "\t" + p.y + "\t" + p.z + "\t" + p.yaw + "\t" + p.pitch);
            w.newLine();
         }
      } catch (IOException e) {
         System.out.println("[moderncmds] could not write " + file + ": " + e);
      } finally {
         if (w != null) try { w.close(); } catch (IOException ignored) { }
      }
   }

   public static synchronized void put(String key, Point p) { POINTS.put(key, p); save(); }
   public static synchronized Point get(String key)         { return POINTS.get(key); }
   public static synchronized boolean remove(String key)    { boolean b = POINTS.remove(key) != null; if (b) save(); return b; }

   public static synchronized List<String> warpNames() {
      List<String> out = new ArrayList<String>();
      for (String k : POINTS.keySet()) if (k.startsWith("warp:")) out.add(k.substring(5));
      return out;
   }
}
