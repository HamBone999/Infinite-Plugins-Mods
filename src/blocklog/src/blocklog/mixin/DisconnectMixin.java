package blocklog.mixin;

import blocklog.LogCommands;
import blocklog.Inspect;
import net.minecraft.server.ServerConfigurationManager;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops per-player state when someone leaves.
 *
 * The last lookup is the one that matters: it can hold tens of thousands of entries alive for
 * a player who logged off hours ago, on a box with 3 GB of RAM. Inspect mode is cleared for a
 * smaller reason -- coming back to find your clicks silently doing nothing is confusing.
 */
@Mixin(ServerConfigurationManager.class)
public class DisconnectMixin {

   // playerLoggedOut is the real disconnect path; removePlayersFromList is never called when
   // a player quits. See basics.mixin.LoginMixin for how that was found.
   @Inject(method = "playerLoggedOut", at = @At("HEAD"))
   private void blocklog$forget(EntityPlayerMP p, CallbackInfo ci) {
      LogCommands.forget(p.getName());
      Inspect.off(p.getName());
   }
}
