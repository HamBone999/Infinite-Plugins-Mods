package sweeper;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/** /sweep -- run one now, or see what the automatic ones have been doing. */
public final class SweepCommands {

   private SweepCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String line) {
      String trimmed = line.startsWith("/") ? line.substring(1) : line;
      String[] args = trimmed.trim().split("\\s+");
      if (args.length == 0 || !args[0].equalsIgnoreCase("sweep")) {
         return false;
      }

      String sub = args.length > 1 ? args[1].toLowerCase() : "now";

      // "sweep.status" is worth granting on its own: it only reports, and someone chasing lag
      // wants to see the numbers without also being able to fire a sweep at a bad moment.
      String node = "sweep." + sub;
      if (!Perms.may(p, server, node)) {
         p.addChatMessage(Perms.denied(node));
         return true;
      }

      if (sub.equals("status")) {
         p.addChatMessage("--- sweeper ---");
         p.addChatMessage("Every " + Config.intervalSeconds + "s. Merge "
               + (Config.merge ? "on (r=" + Config.mergeRadius + ")" : "off")
               + ", item lifetime "
               + (Config.itemLifetimeSeconds > 0 ? Config.itemLifetimeSeconds + "s" : "off")
               + ", mob cap "
               + (Config.maxMobsPerWorld > 0 ? String.valueOf(Config.maxMobsPerWorld) : "off"));
         p.addChatMessage("Since restart: merged " + Sweep.mergedTotal
               + ", removed " + Sweep.expiredTotal + " items, trimmed " + Sweep.trimmedTotal + " mobs.");
      } else if (sub.equals("now")) {
         // This world only. A sweep of every dimension at once is a bigger spike than the
         // problem it is fixing, and the operator asking for it is standing in the laggy one.
         Sweep.Result r = Sweep.run(p.world);
         p.addChatMessage("Swept: " + r.describe() + ".");
      } else {
         p.addChatMessage("/sweep now | /sweep status");
      }
      return true;
   }

   public static List<String> helpLines() {
      List<String> lines = new ArrayList<String>();
      lines.add("/sweep now -- tidy this dimension right away");
      lines.add("/sweep status -- settings and totals");
      return lines;
   }
}
