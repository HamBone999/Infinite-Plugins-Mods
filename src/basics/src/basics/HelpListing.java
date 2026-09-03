package basics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.custom.HelpCategories;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Registers the basics commands for /help, under two headings.
 *
 * Two rather than one for the same reason the server jar splits "Infinite" from "Infinite (op)":
 * ordering op commands last inside a single section is invisible on screen, so /smite reads as
 * something any player might run. A heading says it outright.
 */
public final class HelpListing {

   private static final String PLAYER_CATEGORY = "Basics";
   private static final String OP_CATEGORY = "Basics (op)";

   private HelpListing() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      String[] player = BasicCommands.playerCommands();
      String[] op = BasicCommands.opCommands();

      for (int i = 0; i < player.length; i++) {
         reg(d, player[i], true, false);
      }
      for (int i = 0; i < op.length; i++) {
         reg(d, op[i], true, true);
      }

      try {
         HelpCategories.register(PLAYER_CATEGORY, 0, player);
         HelpCategories.register(OP_CATEGORY, 0, op);

         // Two commands the server jar registers with Brigadier but never files under a
         // heading, so /help printed them in the unheaded block above every section -- which
         // read as though they were a different kind of thing rather than just uncategorised.
         // Filed by who can actually run them: HelpCommand has no requires() so anyone may
         // use /help, while ScoreCommand gates itself on hasPermission.
         //
         // Done from here rather than in CustomCommandHelp because that is in the server jar,
         // and a heading is not worth a server build and a matching client release. This runs
         // at CoreCommands.register TAIL, after the jar has filed everything else, so these
         // are additions to categories that already exist.
         HelpCategories.register("Infinite", 0, "help");
         HelpCategories.register("Infinite (op)", 0, "score");

         // The subcommands are parsed out of the argument string, so a listing built by walking
         // the command tree cannot see them.
         HelpCategories.line(PLAYER_CATEGORY, "/mail read | /mail send <player> <msg> | /mail clear");
         HelpCategories.line(PLAYER_CATEGORY, "/ignore <player> -- stop their messages reaching you");
         HelpCategories.line(PLAYER_CATEGORY, "/near [radius] -- who is close by");
         HelpCategories.line(OP_CATEGORY, "/burn <player> [seconds] | /smite <player>");
         HelpCategories.line(OP_CATEGORY, "/feed [player] | /repair -- the item in your hand");
      } catch (Throwable t) {
         System.out.println("[basics] /help categories unavailable on this server build");
      }
      System.out.println("[basics] " + (player.length + op.length) + " commands registered for /help");
   }

   private static void reg(CommandDispatcher<CommandSourceStack> d, String name,
                           boolean takesArgs, boolean opOnly) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node =
         Commands.literal(name);

      if (opOnly) {
         node = node.requires(new java.util.function.Predicate<CommandSourceStack>() {
            public boolean test(CommandSourceStack src) {
               return src.hasPermission(CommandSourceStack.LEVEL_OP);
            }
         });
      }

      node = node.executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
         public int run(CommandContext<CommandSourceStack> ctx) {
            return dispatch(ctx);
         }
      });

      if (takesArgs) {
         node.then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) {
                  return dispatch(ctx);
               }
            }));
      }

      d.register(node);
   }

   private static int dispatch(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = ctx.getSource();
      net.minecraft.game.entity.player.Player pl = src.getPlayer();
      if (!(pl instanceof EntityPlayerMP)) {
         src.sendFailure("That command can only be run by a player.");
         return 0;
      }
      EntityPlayerMP p = (EntityPlayerMP)pl;
      BasicCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
