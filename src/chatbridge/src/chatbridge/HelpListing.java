package chatbridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.custom.HelpCategories;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Two headings, because the two commands have different audiences: /discord is for players and
 * belongs above the operator block, /chatbridge is administration.
 */
public final class HelpListing {

   private static final String PLAYER_CATEGORY = "Discord";
   private static final String OP_CATEGORY = "Discord (op)";

   private HelpListing() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      reg(d, "discord", false, false);
      reg(d, "chatbridge", true, true);

      try {
         HelpCategories.register(PLAYER_CATEGORY, 0, "discord");
         List<String> lines = BridgeCommands.helpLines();
         for (int i = 0; i < lines.size(); i++) {
            HelpCategories.line(PLAYER_CATEGORY, lines.get(i));
         }

         HelpCategories.register(OP_CATEGORY, 0, "chatbridge");
         List<String> opLines = BridgeCommands.opHelpLines();
         for (int i = 0; i < opLines.size(); i++) {
            HelpCategories.line(OP_CATEGORY, opLines.get(i));
         }
      } catch (Throwable t) {
         System.out.println("[chatbridge] /help categories unavailable on this server build");
      }
      System.out.println("[chatbridge] /discord and /chatbridge registered for /help");
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
      BridgeCommands.handle(p, p.mcServer, ctx.getInput());
      return 1;
   }
}
