package pregen.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pregen.PregenCommands;

/** Runs /pregen before Brigadier sees the line, the same way the other addons do. */
@Mixin(value = NetServerHandler.class, priority = 1100)
public abstract class CommandMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void pregen$commands(String command, CallbackInfo ci) {
      if (PregenCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
