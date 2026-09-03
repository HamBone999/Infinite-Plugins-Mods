package chatbridge.mixin;

import chatbridge.BridgeCommands;
import chatbridge.Relay;
import chatbridge.Spool;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows queued Discord messages, on the server thread, and runs /discord.
 *
 * The poller runs on its own thread and must not touch the world or the player list from there,
 * so it parks lines in {@link Relay} and this drains them somewhere safe.
 *
 * handlePackets is the hook because MinecraftServer has no tick method to inject into -- its
 * loop is inline in run() -- and this runs once per player per tick with mcServer already in
 * scope. The consequence is that nothing drains while the server is empty, which is why Relay
 * drops lines that have gone stale rather than dumping ten minutes of Discord backlog on the
 * first person to log in.
 */
@Mixin(value = NetServerHandler.class, priority = 900)
public abstract class TickMixin {

   @Shadow public MinecraftServer mcServer;
   @Shadow public EntityPlayerMP playerEntity;

   @Inject(method = "handlePackets", at = @At("RETURN"))
   private void chatbridge$drain(CallbackInfo ci) {
      // Draining here as well as in WorldTickMixin keeps the delay to a single tick when
      // somebody is actually online to read it; the world tick is the fallback for an empty
      // server, where nothing here runs at all.
      Spool.drain();
      Relay.flushTo(this.mcServer);
   }

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void chatbridge$commands(String command, CallbackInfo ci) {
      if (BridgeCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
