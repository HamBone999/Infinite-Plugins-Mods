package teleportfix.mixin;

import net.minecraft.game.block.machines.Teleporter;
import net.minecraft.game.entity.player.Player;
import net.minecraft.game.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import teleportfix.Cooldown;
import teleportfix.Diagnostics;

/**
 * Rate limits teleporter use, and reports what each click resolved to.
 *
 * Teleporter is a shared game class, but this addon only loads server side, so the mixin only
 * ever applies there -- which is what we want: the client predicting a refusal it cannot know
 * about would desync it from the server's answer.
 *
 * Verified signature: blockActivated(Lnet/minecraft/game/world/World;IIILnet/minecraft/game/entity/player/Player;)Z
 */
@Mixin(Teleporter.class)
public abstract class TeleporterMixin {

   @Inject(method = "blockActivated", at = @At("HEAD"), cancellable = true)
   private void teleportfix$gate(World world, int x, int y, int z, Player player,
                                 CallbackInfoReturnable<Boolean> cir) {
      if (world.isOnline || player == null) {
         return;
      }

      long wait = Cooldown.check(player.username);
      if (wait > 0L) {
         // Returning true says "the click was handled", which stops the block being used and
         // stops the item in hand being placed instead.
         player.addChatMessage("Teleporter is recharging -- " + ((wait / 100L) / 10.0) + "s.");
         cir.setReturnValue(Boolean.TRUE);
         return;
      }

      Diagnostics.report(world, x, y, z, player);
   }
}
