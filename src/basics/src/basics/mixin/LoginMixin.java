package basics.mixin;

import basics.State;
import basics.Texts;
import java.util.List;
import net.minecraft.server.ServerConfigurationManager;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Greets an arriving player and records a leaving one.
 *
 * The logout time is what /seen reads. It is written on the way out rather than periodically,
 * so a crash loses at most the current session -- /seen then reports the previous logout, which
 * is wrong by one session rather than absent.
 */
@Mixin(ServerConfigurationManager.class)
public class LoginMixin {

   @Inject(method = "playerLoggedIn", at = @At("TAIL"))
   private void basics$welcome(EntityPlayerMP p, CallbackInfo ci) {
      State.clearAfk(p.getName());

      List<String> motd = Texts.motd();
      for (int i = 0; i < motd.size(); i++) {
         p.addChatMessage(motd.get(i));
      }

      int mail = State.mailFor(p.getName()).size();
      if (mail > 0) {
         p.addChatMessage("You have " + mail + " unread message" + (mail == 1 ? "" : "s")
               + ". /mail read");
      }
   }

   /**
    * playerLoggedOut, not removePlayersFromList.
    *
    * removePlayersFromList looks like the disconnect hook and is not: nothing in the server
    * calls it when a player quits. The real path is NetServerHandler.kickPlayer and
    * handleErrorMessage, and both call configManager.playerLoggedOut. Hooking the wrong one
    * cost the first live test -- /seen recorded nobody, because the callback never ran.
    */
   @Inject(method = "playerLoggedOut", at = @At("HEAD"))
   private void basics$farewell(EntityPlayerMP p, CallbackInfo ci) {
      State.markSeen(p.getName());
      State.clearAfk(p.getName());
   }
}
