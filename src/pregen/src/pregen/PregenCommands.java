package pregen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/** /pregen -- generate terrain now so nobody waits for it later. */
public final class PregenCommands {

   /**
    * A cap, not a recommendation. 512 chunks is 8192 blocks out, over a million chunks; the
    * limit is here so a mistyped radius cannot quietly commit the server to a run measured in
    * weeks. Say what it would cost instead and let the operator decide.
    */
   private static final int MAX_RADIUS = 512;

   private PregenCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String line) {
      String trimmed = line.startsWith("/") ? line.substring(1) : line;
      String[] args = trimmed.trim().split("\\s+");
      if (args.length == 0 || !args[0].equalsIgnoreCase("pregen")) {
         return false;
      }

      String sub = args.length > 1 ? args[1].toLowerCase() : "status";
      String node = "pregen." + sub;
      if (!Perms.may(p, server, node)) {
         p.addChatMessage(Perms.denied(node));
         return true;
      }

      if (sub.equals("status")) {
         p.addChatMessage("--- pregen ---");
         p.addChatMessage(Runner.status());
         p.addChatMessage("Budget " + Config.budgetMs + " ms/tick, "
               + (Config.runWithPlayers ? "runs with players online" : "holds while players are online")
               + ".");
      } else if (sub.equals("start")) {
         start(p, args);
      } else if (sub.equals("stop")) {
         p.addChatMessage(Runner.stop());
      } else if (sub.equals("resume")) {
         p.addChatMessage(Runner.resume());
      } else {
         for (String l : helpLines()) {
            p.addChatMessage(l);
         }
      }
      return true;
   }

   private static void start(EntityPlayerMP p, String[] args) {
      if (args.length < 3) {
         p.addChatMessage("/pregen start <radius in chunks> [centreX centreZ]");
         return;
      }

      int radius;
      try {
         radius = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
         p.addChatMessage("'" + args[2] + "' is not a number of chunks.");
         return;
      }
      if (radius < 1 || radius > MAX_RADIUS) {
         p.addChatMessage("Radius must be between 1 and " + MAX_RADIUS + " chunks.");
         return;
      }

      int[] centre;
      if (args.length >= 5) {
         try {
            // Block coordinates, because that is what a player reads off their screen.
            centre = new int[]{Integer.parseInt(args[3]) >> 4, Integer.parseInt(args[4]) >> 4};
         } catch (NumberFormatException e) {
            p.addChatMessage("Centre must be two block coordinates, or leave it out to use spawn.");
            return;
         }
      } else {
         centre = Runner.spawnChunk(p.world);
      }

      p.addChatMessage(Runner.start(p.world, centre[0], centre[1], radius));
      long chunks = (radius * 2L + 1L) * (radius * 2L + 1L);
      // About 1.4 virgin chunks a second, measured on this server rather than worked out from
      // the budget: a new chunk costs more than a whole tick on its own, so the budget only
      // decides how many of the cheap already-generated ones get done together. Ground that
      // already exists goes many times faster, so this is the pessimistic end.
      long minutes = chunks / 84L;   // 1.4 chunks a second, so 84 a minute
      p.addChatMessage("Up to about " + (minutes < 1L ? "a minute" : minutes + " minutes")
            + " on ground this new -- less where it already exists."
            + " It holds whenever anyone logs in.");
   }

   public static List<String> helpLines() {
      List<String> lines = new ArrayList<String>();
      lines.add("/pregen start <radius> [x z] -- generate a square of chunks ahead of time");
      lines.add("/pregen status -- how far along it is");
      lines.add("/pregen stop -- pause it (progress is kept)");
      lines.add("/pregen resume -- carry on where it stopped");
      return lines;
   }
}
