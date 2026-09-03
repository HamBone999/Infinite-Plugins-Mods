package basics.mixin;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.core.HelpCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Collapses the double entry every argument-taking command gets in /help.
 *
 * The listing showed each of these twice:
 *
 *   /setblock
 *   /setblock <args>
 *
 * Not a clash between addons -- it is how the commands are registered. A node built with both
 * .executes(...) and .then(argument("args", greedyString()).executes(...)) has two executable
 * paths through it, and Brigadier's getAllUsage returns one line per path. Every command in
 * CustomCommandHelp's plainArgs and opArgs arrays is built that way, and so is every command
 * the addons register, so all of them doubled.
 *
 * Fixed here rather than at the registrations because the same pattern is in the server jar,
 * and changing that would mean a server build and a matching client release for what is a
 * presentation bug. This also covers any addon that registers the same way in future.
 *
 * The listing keeps one line per command; /help <command> still prints every usage variant,
 * which is where that detail belongs.
 */
@Mixin(HelpCommand.class)
public class HelpDedupeMixin {

   @Inject(method = "collect", at = @At("RETURN"))
   private static void basics$dedupe(CommandDispatcher<CommandSourceStack> dispatcher,
                                     CommandSourceStack source,
                                     List<String> headers,
                                     List<String> lines,
                                     CallbackInfo ci) {
      if (headers == null || lines == null || lines.size() != headers.size()) {
         return;
      }

      // Only an exact repeat, or the same line with " <args>" on the end, is dropped. That is
      // precisely the pair Brigadier produces. It deliberately cannot match the plain lines
      // addons add through HelpCategories.line -- those carry a real subcommand word, like
      // "/rg wand -- select an area", so they survive.
      for (int i = lines.size() - 1; i >= 0; i--) {
         String line = lines.get(i);
         String header = headers.get(i);
         boolean duplicate = false;

         for (int j = 0; j < i; j++) {
            if (!basics$sameSection(header, headers.get(j))) {
               continue;
            }
            String earlier = lines.get(j);
            if (line.equals(earlier) || line.equals(earlier + " <args>")) {
               duplicate = true;
               break;
            }
         }

         if (duplicate) {
            lines.remove(i);
            headers.remove(i);
         }
      }
   }

   /** Headers are null for the unheaded first group, so this cannot just use equals. */
   private static boolean basics$sameSection(String a, String b) {
      return a == null ? b == null : a.equals(b);
   }
}
