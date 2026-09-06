package pregen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import net.minecraft.game.entity.player.Player;
import net.minecraft.game.world.World;
import net.minecraft.game.world.util.Position;
import net.minecraft.server.world.WorldServer;

/**
 * Walks a square of chunks outwards from a centre, generating a few each tick.
 *
 * Single-threaded on purpose. The generator keeps per-instance scratch (its Random is reseeded
 * per chunk, the noise buffers are fields) and toggles the global Sand.can_fall, so two threads
 * in it would not merely race -- they would produce different terrain from the same seed. The
 * cost is paid in wall-clock time instead, which an idle server has plenty of.
 *
 * Progress is written to world/pregen.state every so often, so a restart mid-run resumes rather
 * than starting over.
 */
public final class Runner {

   private static boolean running;
   private static int dim;
   private static int centerX;      // chunk coords
   private static int centerZ;
   private static int radius;       // in chunks
   private static int ring;
   private static int step;         // position within the ring
   private static long generated;   // chunks actually generated this run
   private static long visited;     // chunks reached, including ones already on disk
   private static int ticksSinceReport;
   private static long startedAt;
   private static File stateFile;
   private static String pausedReason;

   private Runner() {
   }

   // ---- state ------------------------------------------------------------

   public static synchronized void load(File file) {
      stateFile = file;
      if (!file.exists()) {
         return;
      }

      Properties p = new Properties();
      InputStream in = null;
      try {
         in = new FileInputStream(file);
         p.load(in);
         dim = intOf(p, "dim", 0);
         centerX = intOf(p, "center-x", 0);
         centerZ = intOf(p, "center-z", 0);
         radius = intOf(p, "radius", 0);
         ring = intOf(p, "ring", 0);
         step = intOf(p, "step", 0);
         visited = intOf(p, "visited", 0);
         running = Boolean.parseBoolean(p.getProperty("running", "false"));
         if (running) {
            startedAt = System.currentTimeMillis();
            System.out.println("[pregen] resuming an unfinished run: " + summary());
         }
      } catch (Exception e) {
         System.out.println("[pregen] could not read " + file + ": " + e);
      } finally {
         close(in);
      }
   }

   private static void save() {
      if (stateFile == null) {
         return;
      }

      OutputStream out = null;
      try {
         stateFile.getParentFile().mkdirs();
         out = new FileOutputStream(stateFile);
         Properties p = new Properties();
         p.setProperty("running", String.valueOf(running));
         p.setProperty("dim", String.valueOf(dim));
         p.setProperty("center-x", String.valueOf(centerX));
         p.setProperty("center-z", String.valueOf(centerZ));
         p.setProperty("radius", String.valueOf(radius));
         p.setProperty("ring", String.valueOf(ring));
         p.setProperty("step", String.valueOf(step));
         p.setProperty("visited", String.valueOf(visited));
         p.store(out, "pregen progress -- safe to delete when not running");
      } catch (Exception e) {
         System.out.println("[pregen] could not write " + stateFile + ": " + e);
      } finally {
         close(out);
      }
   }

   // ---- control ----------------------------------------------------------

   public static synchronized String start(World world, int chunkX, int chunkZ, int radiusChunks) {
      if (running) {
         return "A run is already going. /pregen stop first.";
      }

      dim = world.currDim == null ? 0 : world.currDim.dimension;
      centerX = chunkX;
      centerZ = chunkZ;
      radius = radiusChunks;
      ring = 0;
      step = 0;
      generated = 0L;
      visited = 0L;
      running = true;
      pausedReason = null;
      startedAt = System.currentTimeMillis();
      ticksSinceReport = 0;
      save();
      return "Started: " + total() + " chunks around chunk " + centerX + ", " + centerZ
            + " in dimension " + dim + ".";
   }

   public static synchronized String stop() {
      if (!running) {
         return "Nothing is running.";
      }
      running = false;
      save();
      return "Stopped after " + visited + " of " + total() + " chunks. /pregen resume picks up here.";
   }

   public static synchronized String resume() {
      if (running) {
         return "Already running.";
      }
      if (radius <= 0 || visited >= total()) {
         return "Nothing to resume. /pregen start <radius>";
      }
      running = true;
      pausedReason = null;
      startedAt = System.currentTimeMillis();
      save();
      return "Resumed at " + visited + " of " + total() + " chunks.";
   }

   public static synchronized boolean isRunning() {
      return running;
   }

   public static synchronized String summary() {
      if (radius <= 0) {
         return "idle -- /pregen start <radius>";
      }
      long t = total();
      String where = "centre chunk " + centerX + ", " + centerZ + " r=" + radius + " dim " + dim;
      if (!running) {
         return (visited >= t ? "finished" : "paused at " + visited + "/" + t) + " -- " + where;
      }
      return "running " + visited + "/" + t + " (" + (visited * 100L / Math.max(1L, t)) + "%)"
            + (pausedReason == null ? "" : ", held: " + pausedReason) + " -- " + where;
   }

   public static synchronized String status() {
      StringBuilder sb = new StringBuilder(summary());
      if (running && generated > 0L) {
         long elapsed = Math.max(1L, System.currentTimeMillis() - startedAt) / 1000L;
         long rate = generated / Math.max(1L, elapsed);
         long left = total() - visited;
         sb.append("  ~").append(rate).append(" chunks/s");
         if (rate > 0L) {
            sb.append(", about ").append(left / rate / 60L).append(" min left");
         }
      }
      return sb.toString();
   }

   public static long total() {
      long side = radius * 2L + 1L;
      return side * side;
   }

   // ---- the work ---------------------------------------------------------

   public static synchronized void tick(World world) {
      if (!running || stateFile == null) {
         return;
      }
      if ((world.currDim == null ? 0 : world.currDim.dimension) != dim) {
         return;
      }
      if (!(world instanceof WorldServer)) {
         return;
      }

      if (!Config.runWithPlayers && !world.players.isEmpty()) {
         if (pausedReason == null) {
            pausedReason = "players online";
            System.out.println("[pregen] holding while players are online (run-with-players=false)");
         }
         return;
      }
      if (pausedReason != null) {
         System.out.println("[pregen] resuming: " + summary());
         pausedReason = null;
         startedAt = System.currentTimeMillis();
         generated = 0L;
      }

      WorldServer ws = (WorldServer)world;
      long deadline = System.nanoTime() + Config.budgetMs * 1000000L;

      do {
         if (ring > radius) {
            finish();
            return;
         }

         int cx = centerX + ringX();
         int cz = centerZ + ringZ();
         boolean already = ws.chunkProviderServer.chunkExists(cx, cz);
         ws.chunkProviderServer.loadChunk(cx, cz);
         if (!already) {
            generated++;
            // Hand it straight back for unloading, or a large run would hold every chunk it
            // ever touched in memory. The unload path is also what writes it to disk.
            ws.chunkProviderServer.addChunk(cx, cz);
         }
         visited++;
         advance();
      } while (System.nanoTime() < deadline && ring <= radius);

      if (++ticksSinceReport >= Config.reportEveryTicks) {
         ticksSinceReport = 0;
         System.out.println("[pregen] " + status());
         save();
      }
   }

   private static void finish() {
      running = false;
      System.out.println("[pregen] finished: " + visited + " chunks around " + centerX + ", "
            + centerZ + " (r=" + radius + ", dim " + dim + ")");
      save();
   }

   /**
    * Step to the next chunk, walking rings outwards from the centre.
    *
    * Ring r has 8r cells, in four legs of 2r. Outwards order matters: the chunks nearest the
    * centre are the ones players reach first, so a run that is stopped early still leaves the
    * useful part done.
    */
   private static void advance() {
      if (ring == 0) {
         ring = 1;
         step = 0;
         return;
      }
      if (++step >= 8 * ring) {
         ring++;
         step = 0;
      }
   }

   private static int ringX() {
      if (ring == 0) {
         return 0;
      }
      int leg = step / (2 * ring);
      int t = step % (2 * ring);
      switch (leg) {
         case 0:
            return ring;
         case 1:
            return ring - t;
         case 2:
            return -ring;
         default:
            return -ring + t;
      }
   }

   private static int ringZ() {
      if (ring == 0) {
         return 0;
      }
      int leg = step / (2 * ring);
      int t = step % (2 * ring);
      switch (leg) {
         case 0:
            return -ring + t;
         case 1:
            return ring;
         case 2:
            return ring - t;
         default:
            return -ring;
      }
   }

   /** Where a player is standing, in chunk coordinates. */
   public static int[] chunkOf(Player p) {
      return new int[]{(int)Math.floor(p.posX) >> 4, (int)Math.floor(p.posZ) >> 4};
   }

   /** The world spawn, in chunk coordinates. */
   public static int[] spawnChunk(World world) {
      Position spawn = world.getSpawnPoint();
      int x = spawn == null ? 0 : spawn.x;
      int z = spawn == null ? 0 : spawn.z;
      return new int[]{x >> 4, z >> 4};
   }

   private static int intOf(Properties p, String key, int fallback) {
      try {
         return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
      } catch (NumberFormatException e) {
         return fallback;
      }
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
