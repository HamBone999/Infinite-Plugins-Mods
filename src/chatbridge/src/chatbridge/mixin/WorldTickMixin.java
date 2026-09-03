package chatbridge.mixin;

import chatbridge.Spool;
import chatbridge.Status;
import net.minecraft.game.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the status file current and the spool drained even with nobody online.
 *
 * The other tick hook lives on handlePackets, which runs once per player per tick -- so on an
 * empty server it does not run at all. That is exactly when someone asks the bot whether the
 * server is up, and a status file that stopped updating the moment the last player left would
 * have the bot report the server as down whenever it was merely quiet. It also let the spool
 * grow without bound while nobody was on to receive from it.
 *
 * World.tickBlocks runs every tick regardless, so this is the honest place for both.
 */
@Mixin(World.class)
public abstract class WorldTickMixin {

   @Inject(method = "tickBlocks", at = @At("RETURN"))
   private void chatbridge$idleTick(CallbackInfo ci) {
      Object self = this;
      if (!(self instanceof WorldServer)) {
         return;
      }
      // One world is enough: the status is server-wide, and draining twice a tick is harmless
      // but pointless. Dimension 0 is always loaded.
      World world = (World)self;
      if (world.currDim == null || world.currDim.dimension != 0) {
         return;
      }

      MinecraftServer server = ((WorldServerAccessor)self).getMcServer();
      Spool.drain();
      Status.maybeWrite(server);
   }
}
