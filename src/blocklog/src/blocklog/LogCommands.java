package blocklog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.game.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * /bl -- the whole command surface.
 *
 * Parsed here rather than through Brigadier because the filter syntax (u:name t:2h r:20) is not
 * a command tree; it is a bag of optional key:value tokens in any order, which Brigadier models
 * badly. HelpListing registers a stub node so /help still lists it -- the same split every other
 * addon here uses.
 */
public final class LogCommands {

   /** Last lookup per player, so /bl page 2 can continue it without re-running the filter. */
   private static final Map<String, List<Entry>> LAST = new HashMap<String, List<Entry>>();

   private LogCommands() {
   }

   /** Returns true if the line was ours and has been dealt with. */
   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String line) {
      String trimmed = line.startsWith("/") ? line.substring(1) : line;
      String[] args = trimmed.trim().split("\\s+");
      if (args.length == 0) {
         return false;
      }

      String cmd = args[0].toLowerCase();
      if (!cmd.equals("bl") && !cmd.equals("blocklog")) {
         return false;
      }

      if (args.length == 1) {
         usage(p);
         return true;
      }

      String sub = args[1].toLowerCase();

      // Permission is per subcommand, not per command. Reading the log and rewriting the world
      // are very different powers: a moderator wants to know who broke a wall, and giving them
      // "bl" so they can find out would also hand them /bl rollback and /bl purge. Granting
      // "bl.lookup" and "bl.wand" answers the first without the second.
      String node = "bl." + canonical(sub);
      // Usage is free to anyone who got this far. perms has already decided whether they may
      // run /bl at all; refusing to tell them what it does would be a strange place to stop.
      if (!node.equals("bl.help") && !Perms.may(p, server, node)) {
         p.addChatMessage(Perms.denied(node));
         return true;
      }

      if (sub.equals("help") || sub.equals("?")) {
         usage(p);
      } else if (sub.equals("wand") || sub.equals("w")) {
         wand(p);
      } else if (sub.equals("inspect") || sub.equals("i")) {
         boolean on = Inspect.toggle(p.getName());
         p.addChatMessage(on
               ? "Inspect on. Hit or right-click a block to see its history. /bl i to stop."
               : "Inspect off.");
      } else if (sub.equals("lookup") || sub.equals("l")) {
         lookup(p, args);
      } else if (sub.equals("page") || sub.equals("p")) {
         page(p, args);
      } else if (sub.equals("rollback") || sub.equals("rb")) {
         apply(p, server, args, true);
      } else if (sub.equals("restore") || sub.equals("rs")) {
         apply(p, server, args, false);
      } else if (sub.equals("purge")) {
         purge(p, args);
      } else if (sub.equals("status") || sub.equals("s")) {
         status(p);
      } else {
         p.addChatMessage("Unknown /bl command '" + sub + "'. Try /bl help.");
      }
      return true;
   }

   /**
    * Hands over the wand.
    *
    * Not stacked and not re-issued if one is already in the inventory: the wand is a tool, and
    * finding six of them spread across the hotbar after a few uses is its own small annoyance.
    */
   private static void wand(EntityPlayerMP p) {
      if (WandItem.isHeld(p)) {
         p.addChatMessage("You are already holding it. Click any block to see its history.");
         return;
      }
      if (!WandItem.give(p)) {
         p.addChatMessage("Could not give you the wand -- no room, or the block registry is unavailable.");
         return;
      }
      p.addChatMessage("Given the " + WandItem.DISPLAY_NAME + ".");
      p.addChatMessage("Click any block with it to see who changed it. It will not place.");
   }

   /**
    * The node name for a subcommand, with aliases folded onto the long form.
    *
    * Without this "bl.rb" and "bl.rollback" would be different permissions, and which one a
    * group needed would depend on what the player happened to type.
    */
   private static String canonical(String sub) {
      if (sub.equals("w")) return "wand";
      if (sub.equals("i")) return "inspect";
      if (sub.equals("l")) return "lookup";
      if (sub.equals("p")) return "page";
      if (sub.equals("rb")) return "rollback";
      if (sub.equals("rs")) return "restore";
      if (sub.equals("s")) return "status";
      if (sub.equals("?")) return "help";
      return sub;
   }

   private static void usage(EntityPlayerMP p) {
      p.addChatMessage("--- /bl ---");
      p.addChatMessage("/bl wand -- enchanted bedrock; click a block to see its history");
      p.addChatMessage("/bl inspect -- the same, without holding anything");
      p.addChatMessage("/bl lookup <filters> -- search the log");
      p.addChatMessage("/bl rollback <filters> -- undo those changes");
      p.addChatMessage("/bl restore <filters> -- put them back");
      p.addChatMessage("/bl page <n> -- more of the last lookup");
      p.addChatMessage("/bl purge [days] | /bl status");
      p.addChatMessage("Filters: u:name  t:2h  r:20  a:break|place  global");
      p.addChatMessage("Example: /bl rollback u:griefer t:1h r:30");
   }

   private static void lookup(EntityPlayerMP p, String[] args) {
      Log.flush();
      Query q = parse(p, args);
      if (q == null) {
         return;
      }

      List<Entry> found = Log.query(q);
      LAST.put(p.getName().toLowerCase(), found);

      p.addChatMessage("--- " + found.size() + " changes (" + q.describe() + ") ---");
      if (found.isEmpty()) {
         return;
      }
      showPage(p, found, 1);
   }

   private static void page(EntityPlayerMP p, String[] args) {
      List<Entry> found = LAST.get(p.getName().toLowerCase());
      if (found == null || found.isEmpty()) {
         p.addChatMessage("Nothing to page through. Run /bl lookup first.");
         return;
      }
      int n = 1;
      if (args.length > 2) {
         try {
            n = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            p.addChatMessage("'" + args[2] + "' is not a page number.");
            return;
         }
      }
      showPage(p, found, n);
   }

   /** Newest first, which is the order you read a history in. */
   private static void showPage(EntityPlayerMP p, List<Entry> found, int page) {
      int per = Config.pageSize;
      int pages = (found.size() + per - 1) / per;
      if (page < 1) {
         page = 1;
      }
      if (page > pages) {
         p.addChatMessage("There " + (pages == 1 ? "is 1 page" : "are " + pages + " pages") + ".");
         return;
      }

      int start = (page - 1) * per;
      for (int i = 0; i < per; i++) {
         int idx = found.size() - 1 - (start + i);
         if (idx < 0) {
            break;
         }
         p.addChatMessage(Format.lineWithPos(found.get(idx)));
      }
      if (pages > 1) {
         p.addChatMessage("Page " + page + "/" + pages + (page < pages ? " -- /bl page " + (page + 1) : ""));
      }
   }

   /**
    * Rollback and restore are the same walk in opposite directions.
    *
    * Rollback goes newest to oldest writing the old state, so a block broken and then rebuilt
    * ends up as it started rather than as whatever the middle step left. Restore goes oldest to
    * newest writing the new state, for the same reason in reverse.
    */
   private static void apply(EntityPlayerMP p, MinecraftServer server, String[] args, boolean rollback) {
      Log.flush();
      Query q = parse(p, args);
      if (q == null) {
         return;
      }

      // A bare /bl rollback with no filter would mean "undo everything held", which is never
      // what anyone means and is not recoverable from in-game.
      if (q.actors.isEmpty() && q.since == 0L && q.radius < 0) {
         p.addChatMessage("Refusing an unfiltered " + (rollback ? "rollback" : "restore") + ".");
         p.addChatMessage("Give at least one of u:name, t:2h or r:20.");
         return;
      }

      List<Entry> found = Log.query(q);
      if (found.isEmpty()) {
         p.addChatMessage("Nothing matched (" + q.describe() + ").");
         return;
      }
      if (found.size() > Config.maxRollback) {
         p.addChatMessage(found.size() + " changes matched, over the " + Config.maxRollback + " limit.");
         p.addChatMessage("Narrow the filter, or raise max-rollback-blocks in the config.");
         return;
      }

      int changed = 0;
      int skipped = 0;

      // Nothing here sets an Actor, so the writes below are not themselves logged. That is
      // deliberate: a rollback that logged its own writes would show up in the next lookup as
      // the admin having placed every block back, burying the grief it was undoing.
      for (int i = 0; i < found.size(); i++) {
         Entry e = found.get(rollback ? found.size() - 1 - i : i);

         World world = server.getWorldManager(e.dim);
         if (world == null) {
            skipped++;
            continue;
         }
         // Same guard the // bulk editor uses. Writing into a chunk that is not loaded forces
         // generation mid-command and drags the server into "Can't keep up".
         if (!world.blockExists(e.x, e.y, e.z)) {
            skipped++;
            continue;
         }

         int id = rollback ? e.oldId : e.newId;
         int meta = rollback ? e.oldMeta : e.newMeta;
         if (world.setBlockAndMetadataWithNotify(e.x, e.y, e.z, id, meta)) {
            changed++;
         } else {
            skipped++;
         }
      }

      p.addChatMessage((rollback ? "Rolled back " : "Restored ") + changed + " blocks ("
            + q.describe() + ").");
      if (skipped > 0) {
         p.addChatMessage(skipped + " skipped -- unloaded chunks, or already in that state.");
      }
      System.out.println("[blocklog] " + p.getName() + " " + (rollback ? "rollback" : "restore")
            + " " + changed + " blocks, " + skipped + " skipped (" + q.describe() + ")");
   }

   private static void purge(EntityPlayerMP p, String[] args) {
      int days = Config.purgeDays;
      if (args.length > 2) {
         try {
            days = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            p.addChatMessage("'" + args[2] + "' is not a number of days.");
            return;
         }
      }
      if (days < 1) {
         p.addChatMessage("Refusing to purge everything. Give at least 1 day.");
         return;
      }

      long cutoff = System.currentTimeMillis() / 1000L - days * 86400L;
      p.addChatMessage("Purging entries older than " + days + " days...");
      long dropped = Log.purge(cutoff);
      p.addChatMessage("Done. " + dropped + " dropped from memory; see the console for the file.");
   }

   private static void status(EntityPlayerMP p) {
      Log.flush();
      p.addChatMessage("--- blocklog ---");
      p.addChatMessage("Holding " + Log.held() + " of " + Config.memoryEntries + " entries in memory.");
      p.addChatMessage("Logged " + Log.writtenThisRun() + " changes since the last restart.");
      p.addChatMessage("Log file: " + (Log.fileSize() / 1024L) + " KB.");
      p.addChatMessage("Explosions are " + (Config.logExplosions ? "logged" : "not logged") + ".");
   }

   /** Shared parse + error reporting. Returns null once the player has been told what was wrong. */
   private static Query parse(EntityPlayerMP p, String[] args) {
      int x = (int)Math.floor(p.posX);
      int y = (int)Math.floor(p.posY);
      int z = (int)Math.floor(p.posZ);

      Query q = Query.parse(args, 2, p.dimension, x, y, z);
      if (q.error != null) {
         p.addChatMessage(q.error);
         return null;
      }
      return q;
   }

   /** Dropped when a player leaves, so their last lookup is not held forever. */
   public static synchronized void forget(String player) {
      LAST.remove(player.toLowerCase());
   }

   /** Exposed for the help listing so the two cannot drift apart. */
   public static List<String> helpLines() {
      List<String> lines = new ArrayList<String>();
      lines.add("/bl wand -- enchanted bedrock; click a block to see its history");
      lines.add("/bl inspect -- the same, without holding anything");
      lines.add("/bl lookup <filters> | /bl page <n>");
      lines.add("/bl rollback <filters> | /bl restore <filters>");
      lines.add("/bl purge [days] | /bl status");
      lines.add("filters: u:name  t:2h  r:20  a:break|place  global");
      return lines;
   }
}
