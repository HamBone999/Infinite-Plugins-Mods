package basics.mixin;

import basics.BasicCommands;
import basics.State;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the basics commands, and enforces /ignore.
 *
 * priority 1200 so this sees a command line before the server jar's own handler does. That
 * matters for /msg: the jar owns the command, so the only way to stop a message reaching
 * someone who has been ignored is to refuse the line before the jar ever parses it.
 */
@Mixin(value = NetServerHandler.class, priority = 1200)
public abstract class CommandMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void basics$commands(String command, CallbackInfo ci) {
      // Typing anything is proof you are back at the keyboard.
      basics$noLongerAfk();

      if (basics$blockedByIgnore(command)) {
         // Silent to the sender, on purpose: telling them they are ignored turns /ignore into
         // an announcement and invites them to go around it.
         ci.cancel();
         return;
      }

      if (BasicCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }

   @Inject(method = "handleChat", at = @At("HEAD"))
   private void basics$chatClearsAfk(ChatPacket packet, CallbackInfo ci) {
      basics$noLongerAfk();
   }

   private void basics$noLongerAfk() {
      String name = this.playerEntity.getName();
      if (!State.isAfk(name)) {
         return;
      }
      State.clearAfk(name);
      this.mcServer.configManager.sendPacketToAll(new ChatPacket(name + " is no longer AFK."));
   }

   /** True for "/msg <someone who ignores me> ...". */
   private boolean basics$blockedByIgnore(String command) {
      String line = command.startsWith("/") ? command.substring(1) : command;
      String[] args = line.trim().split("\\s+");
      if (args.length < 3) {
         return false;
      }
      String cmd = args[0].toLowerCase();
      if (!cmd.equals("msg") && !cmd.equals("tell") && !cmd.equals("w")) {
         return false;
      }
      return State.ignores(args[1], this.playerEntity.getName());
   }
}
