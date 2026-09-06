package teleportfix;

import net.minecraft.game.entity.player.Player;
import net.minecraft.game.world.World;
import net.minecraft.game.world.util.Position;
import net.minecraft.game.world.util.TeleporterRegistry;

/**
 * TEMPORARY. Reports what a teleporter click actually resolved to, so "output is missing"
 * under rapid use can be explained instead of guessed at.
 *
 * It repeats the block's own lookup rather than hooking into it -- the block computes its
 * target inside a method we only see the head of, and reading the same two sources gives the
 * same answer without changing what the block does. Remove once the cause is known.
 */
public final class Diagnostics {

   public static boolean enabled = true;

   private Diagnostics() {
   }

   public static void report(World world, int x, int y, int z, Player player) {
      if (!enabled) {
         return;
      }
      try {
         int meta = world.getBlockMetadata(x, y, z);
         int slot = meta < 6 ? meta : meta - 6;
         boolean wantsOutput = meta < 6;

         Position own = null;
         Position[] mine = wantsOutput
               ? player.teleporterPositionsOut.get(player.dimension)
               : player.teleporterPositionsIn.get(player.dimension);
         if (mine != null && slot >= 0 && slot < mine.length) {
            own = mine[slot];
         }

         Position shared = wantsOutput
               ? TeleporterRegistry.getOutput(world, player.dimension, slot)
               : TeleporterRegistry.getInput(world, player.dimension, slot);

         System.out.println("[tpdbg] " + player.username
               + " clicked " + x + "," + y + "," + z
               + "  meta=" + meta + " slot=" + slot + " wants=" + (wantsOutput ? "OUT" : "IN")
               + "  dim=" + player.dimension
               + "  own=" + fmt(own)
               + "  registry=" + fmt(shared)
               + "  blockId=" + world.getBlockId(x, y, z));
      } catch (Throwable t) {
         System.out.println("[tpdbg] report failed: " + t);
      }
   }

   private static String fmt(Position p) {
      return p == null ? "null" : (p.x + "," + p.y + "," + p.z);
   }
}
