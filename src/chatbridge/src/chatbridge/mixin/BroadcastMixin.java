package chatbridge.mixin;

import chatbridge.Config;
import chatbridge.Outbound;
import chatbridge.Relay;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.ServerConfigurationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Everything the server says to everyone, forwarded to Discord.
 *
 * Hooked at the broadcast rather than at handleChat on purpose. Player chat, joins, leaves,
 * deaths and /say all end up here as a ChatPacket, so one hook covers the lot -- and it sees
 * the message after perms has formatted it, instead of racing perms for injection priority
 * (perms cancels handleChat at priority 600 and re-broadcasts, so a handleChat hook would see
 * either the raw or the formatted line depending on which mixin applied first).
 */
@Mixin(ServerConfigurationManager.class)
public class BroadcastMixin {

   @Inject(method = "sendPacketToAll", at = @At("HEAD"))
   private void chatbridge$forward(Packet packet, CallbackInfo ci) {
      if (!(packet instanceof ChatPacket)) {
         return;
      }
      // This broadcast is us showing a Discord message in game. Sending it back would loop.
      if (Relay.suppressed()) {
         return;
      }

      String message = ((ChatPacket)packet).message;
      if (message == null || message.length() == 0) {
         return;
      }
      if (!chatbridge$wanted(message)) {
         return;
      }
      Outbound.send(message);
   }

   /**
    * Which of the three toggles this line falls under.
    *
    * The server does not tag its broadcasts by kind -- they are all just chat -- so this reads
    * the text. Joins and leaves are matched on the exact wording NetServerHandler produces;
    * anything shaped like "Name: something" is player chat; everything else is a server message,
    * which in practice means deaths and /say. A wording change upstream moves a line from one
    * toggle to another, which is a mis-categorised relay rather than a broken one.
    */
   private boolean chatbridge$wanted(String message) {
      if (message.contains(" joined the game") || message.contains(" left the game")) {
         return Config.relayJoinLeave;
      }
      int colon = message.indexOf(": ");
      if (colon > 0 && !message.startsWith("[Discord]")) {
         return Config.relayChat;
      }
      return Config.relayDeaths;
   }
}
