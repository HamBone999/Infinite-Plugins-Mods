package sweeper.mixin;

import net.minecraft.game.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweeper.Schedulers;

/**
 * The tick that drives everything. World.tickBlocks()V
 *
 * At RETURN rather than HEAD: the sweep marks entities dead, and doing that after the world has
 * finished its own pass over them keeps the two out of each other's way.
 */
@Mixin(World.class)
public abstract class TickMixin {

   @Inject(method = "tickBlocks", at = @At("RETURN"))
   private void sweeper$tick(CallbackInfo ci) {
      Schedulers.tick((World)(Object)this);
   }
}
