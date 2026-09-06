package pregen.mixin;

import net.minecraft.game.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pregen.Runner;

/**
 * Drives the generator from the world tick, which runs whether or not anyone is online -- and
 * an empty server is exactly when this is meant to work.
 *
 * At RETURN so the tick's own work is already done and the leftover time is what gets spent.
 */
@Mixin(World.class)
public abstract class TickMixin {

   @Inject(method = "tickBlocks", at = @At("RETURN"))
   private void pregen$tick(CallbackInfo ci) {
      World world = (World)(Object)this;
      if (world.isOnline) {
         return;   // a client-side world has no generator to drive
      }
      Runner.tick(world);
   }
}
