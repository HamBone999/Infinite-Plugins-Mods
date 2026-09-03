package blocklog;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.custom.HelpCategories;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Puts /bl into /help under its own heading.
 *
 * Only /bl is a command node. The subcommands are parsed out of the argument string, so a
 * listing built by walking the command tree cannot see them -- they are added as plain lines,
 * taken from {@link LogCommands#helpLines()} so the two cannot drift apart.
 */
public final class HelpListing {

   private static final String CATEGORY = "Block Log (op)";

   private HelpListing() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node =
         Commands.literal("bl")
            .requires(new java.util.function.Predicate<CommandSourceStack>() {
               public boolean test(CommandSourceStack src) {
                  return src.hasPermission(CommandSourceStack.LEVEL_OP);
               }
            })
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) {
                  return dispatch(ctx);
               }
            });

      node.then(Commands.argument("args", StringArgumentType.greedyString())
         .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
            public int run(CommandContext<CommandSourceStack> ctx) {
               return dispatch(ctx);
            }
         }));

      d.register(node);

      // Wrapped because HelpCategories lives in the server jar: an older server without it
      // should leave these uncategorised rather than stop the addon loading.
      try {
         HelpCategories.register(CATEGORY, "bl");
         List<String> lines = LogCommands.helpLines();
         for (int i = 0; i < lines.size(); i++) {
            HelpCategories.line(CATEGORY, lines.get(i));
         }
      } catch (Throwable t) {
         System.out.println("[blocklog] /help categories unavailable on this server build");
      }
      System.out.println("[blocklog] /bl registered for /help");
   }

   private static int dispatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      net.minecraft.game.entity.player.Player pl = src.getPlayer();
      if (!(pl instanceof EntityPlayerMP)) {
         src.sendFailure("That command can only be run by a player.");
         return 0;
      }
      EntityPlayerMP p = (EntityPlayerMP)pl;
      LogCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
