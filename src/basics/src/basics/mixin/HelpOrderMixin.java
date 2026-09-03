package basics.mixin;

import basics.HelpOrder;
import java.util.List;
import net.minecraft.commands.custom.HelpCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sorts the /help sections.
 *
 * Done out here rather than by changing HelpCategories itself because that class is in the
 * server jar: fixing it there would mean a new server build, a version bump and a matching
 * client release, for what is a presentation choice. categories() already returns a fresh copy
 * each call, so replacing the return value changes nothing else.
 */
@Mixin(HelpCategories.class)
public class HelpOrderMixin {

   @Inject(method = "categories", at = @At("RETURN"), cancellable = true)
   private static void basics$sort(CallbackInfoReturnable<List<String>> cir) {
      List<String> categories = cir.getReturnValue();
      if (categories == null || categories.size() < 2) {
         return;
      }
      cir.setReturnValue(HelpOrder.sort(categories));
   }
}
