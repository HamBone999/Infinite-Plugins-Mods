package moderncmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Registers our commands as real Brigadier nodes so /help lists them.
 *
 * HelpCommand walks dispatcher.getRoot().getChildren() and filters with
 * CommandNode.canUse(source), so a registered node shows up automatically and
 * requires(...) gives per-permission filtering for free.
 *
 * Plain class, not a mixin: Mixin rejects non-private statics, and lambdas are far
 * safer outside a merged class. The mixin is a one-liner that calls in here.
 *
 * Execution normally still goes through the handleSlashCommand hook, which runs first.
 * These executors are the fallback path, and they call the same handler, so the two
 * routes cannot disagree.
 */
public final class HelpListing {

   private HelpListing() { }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      // NOTE: "whitelist" is deliberately absent -- vanilla already lists it, and our
      // subcommands attach to that existing name. Registering it again would double it.
      String[] plain = { "commands", "home", "sethome", "delhome", "spawn", "back",
                         "list", "seed", "warps", "tpaccept", "tpdeny", "r" };
      String[] withArgs = { "warp", "tpa", "msg" };
      String[] opPlain = { "setspawn", "heal" };
      String[] opArgs = { "setwarp", "delwarp", "time", "weather" };

      for (int i = 0; i < plain.length; i++)    register(d, plain[i], false, false);
      for (int i = 0; i < withArgs.length; i++) register(d, withArgs[i], true, false);
      for (int i = 0; i < opPlain.length; i++)  register(d, opPlain[i], false, true);
      for (int i = 0; i < opArgs.length; i++)   register(d, opArgs[i], true, true);

      System.out.println("[moderncmds] " + (plain.length + withArgs.length + opPlain.length + opArgs.length)
            + " commands registered for /help");
   }

   private static void register(CommandDispatcher<CommandSourceStack> d, final String name,
                                boolean takesArgs, boolean opOnly) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);
      if (opOnly) {
         node = node.requires(new java.util.function.Predicate<CommandSourceStack>() {
            public boolean test(CommandSourceStack src) {
               return src.hasPermission(CommandSourceStack.LEVEL_OP);
            }
         });
      }
      node = node.executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
         public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
      });
      if (takesArgs) {
         node.then(Commands.argument("args", StringArgumentType.greedyString())
            .executes(new com.mojang.brigadier.Command<CommandSourceStack>() {
               public int run(CommandContext<CommandSourceStack> ctx) { return dispatch(ctx); }
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
      EntityPlayerMP p = (EntityPlayerMP) pl;
      ModernCommands.handle(p.playerNetServerHandler, p, p.mcServer, ctx.getInput());
      return 1;
   }
}
