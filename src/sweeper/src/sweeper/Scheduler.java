package sweeper;

import net.minecraft.game.world.World;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.game.entity.player.Player;

/**
 * Decides when a sweep happens, and warns first if the sweep will delete anything.
 *
 * Driven from World.tickBlocks rather than a timer thread, because everything the sweep touches
 * is world state and must run on the server thread. Each world keeps its own countdown, so the
 * overworld and the Crimson do not sweep in the same tick and double the spike.
 *
 * The warning only appears when something will actually be deleted. A merge takes nothing away
 * from anyone, and "CLEARING ITEMS IN 10 SECONDS" every minute for a housekeeping pass that
 * removes nothing just teaches people to ignore it.
 */
public final class Scheduler {

   private int ticksUntilSweep;
   private boolean warned;

   /** 20 ticks a second, which is what the server aims for even when it is behind. */
   private static final int TPS = 20;

   public Scheduler() {
      this.ticksUntilSweep = Config.intervalSeconds * TPS;
   }

   public void tick(World world) {
      this.ticksUntilSweep--;

      if (deletesThings() && Config.warnSeconds > 0 && !this.warned
            && this.ticksUntilSweep <= Config.warnSeconds * TPS
            && this.ticksUntilSweep > 0) {
         this.warned = true;
         announce(world, "Clearing dropped items in " + Config.warnSeconds + " seconds.");
      }

      if (this.ticksUntilSweep > 0) {
         return;
      }

      this.ticksUntilSweep = Config.intervalSeconds * TPS;
      this.warned = false;

      Sweep.Result r = Sweep.run(world);
      if (r.expired > 0) {
         announce(world, "Cleared " + r.expired + " dropped items.");
      }
      if (r.didAnything()) {
         System.out.println("[sweeper] " + r.describe());
      }
   }

   private static boolean deletesThings() {
      return Config.itemLifetimeSeconds > 0;
   }

   /**
    * Told to the players in this world only.
    *
    * There is no per-world broadcast on the config manager, so this walks the world's own player
    * list. Someone in the Crimson does not need to hear that the overworld tidied up.
    */
   private static void announce(World world, String message) {
      if (world.players == null) {
         return;
      }
      for (int i = 0; i < world.players.size(); i++) {
         Player p = world.players.get(i);
         if (p instanceof net.minecraft.server.player.EntityPlayerMP) {
            ((net.minecraft.server.player.EntityPlayerMP)p)
                  .playerNetServerHandler.sendPacket(new ChatPacket(message));
         }
      }
   }
}
