package moderncmds.mixin;

import moderncmds.ModernCommands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts before the vanilla dispatcher, so unknown commands still reach it and
 * still print "Unknown command." Only commands we actually handle are cancelled.
 *
 * Verified: NetServerHandler.handleSlashCommand(Ljava/lang/String;)V  (private)
 */
@Mixin(NetServerHandler.class)
public abstract class CommandMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void moderncmds$intercept(String command, CallbackInfo ci) {
      NetServerHandler self = (NetServerHandler) (Object) this;
      if (ModernCommands.handle(self, this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
