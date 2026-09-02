package hamfix.mixin;

import hamfix.TickState;
import net.minecraft.game.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the window during which chunk generation must not happen. World.tickBlocks()V */
@Mixin(World.class)
public abstract class TickBlocksMixin {

   @Inject(method = "tickBlocks", at = @At("HEAD"))
   private void hamfix$enter(CallbackInfo ci) { TickState.enter(); }

   @Inject(method = "tickBlocks", at = @At("RETURN"))
   private void hamfix$exit(CallbackInfo ci) { TickState.exit(); }
}
