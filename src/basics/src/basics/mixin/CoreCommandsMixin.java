package basics.mixin;

import basics.HelpListing;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CoreCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the basics commands to the Brigadier root so /help lists them. */
@Mixin(CoreCommands.class)
public class CoreCommandsMixin {

   @Inject(method = "register", at = @At("TAIL"))
   private static void basics$addToHelp(CommandDispatcher<CommandSourceStack> d, CallbackInfo ci) {
      HelpListing.register(d);
   }
}
