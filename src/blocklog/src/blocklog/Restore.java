package blocklog;

import net.minecraft.network.packet.world.UpdateBlockPacket;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Puts a block back on the client after a click was refused.
 *
 * The client breaks blocks optimistically -- it removes the block the moment you click and
 * waits to be told otherwise -- so cancelling a dig server-side is only half the job. Without
 * this the block stays missing on screen until something else happens to resend that chunk,
 * which is the "I broke it with the wand and now there is a hole" bug.
 *
 * This is the game's own idiom, not an invention: NetServerHandler.handleBlockDig sends exactly
 * this packet when spawn protection refuses a break, and again at status 2 when the block
 * survived the dig. The gap it leaves is status 0, the instant break, where the wands return
 * before reaching either of those.
 *
 * Deliberately duplicated in each addon rather than shared. The addons load in an order this
 * one does not control -- verified as anticheat, worldprotect, landclaim, blocklog -- and
 * whichever one cancels a click is the only one that still gets to answer for it, so each
 * needs its own copy rather than a call into a sibling that may not have loaded.
 */
public final class Restore {

   private Restore() {
   }

   public static void block(EntityPlayerMP p, int x, int y, int z) {
      try {
         p.playerNetServerHandler.sendPacket(
               new UpdateBlockPacket(x, y, z, p.mcServer.getWorldManager(p.dimension)));
      } catch (Throwable t) {
         // A failed cosmetic resend must never take the click handler down with it.
         System.out.println("[blocklog] could not restore the block at "
               + x + "," + y + "," + z + ": " + t);
      }
   }
}
