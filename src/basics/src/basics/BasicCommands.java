package basics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.game.entity.misc.LightningBolt;
import net.minecraft.game.entity.player.Player;
import net.minecraft.game.item.ItemStack;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The commands a server is expected to have that this one did not.
 *
 * What is deliberately NOT here: /tpahere, /tpcancel and /homes. The teleport-request queue and
 * the home list live in the server jar, and a second implementation out here could not see that
 * state -- /tpahere would need its own accept command, so the server would have two /tpaccepts
 * that each only worked for half the requests. Those belong in the jar alongside /tpa, not in
 * an addon.
 */
public final class BasicCommands {

   private BasicCommands() {
   }

   /** Commands anyone may run. */
   private static final String[] PLAYER = {
      "afk", "near", "ping", "me", "rules", "motd", "seen", "mail", "ignore", "helpop", "suicide"
   };

   /** Commands that need op. */
   private static final String[] OP = {
      "tphere", "feed", "repair", "burn", "smite", "top", "broadcast"
   };

   public static String[] playerCommands() {
      return PLAYER.clone();
   }

   public static String[] opCommands() {
      return OP.clone();
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String line) {
      String trimmed = line.startsWith("/") ? line.substring(1) : line;
      String[] args = trimmed.trim().split("\\s+");
      if (args.length == 0) {
         return false;
      }
      String cmd = args[0].toLowerCase();

      boolean isPlayerCmd = contains(PLAYER, cmd);
      boolean isOpCmd = contains(OP, cmd);
      if (!isPlayerCmd && !isOpCmd) {
         return false;
      }

      // Each of these is its own top-level command, so the node is just the command name --
      // exactly the shape perms already uses for kick, ban and gamemode. A group granting
      // "feed,repair,top" reads the same as the entries that were already in that file.
      if (isOpCmd && !Perms.may(p, server, cmd)) {
         p.addChatMessage(Perms.denied(cmd));
         return true;
      }

      if (cmd.equals("afk")) {
         afk(p, server);
      } else if (cmd.equals("near")) {
         near(p, args);
      } else if (cmd.equals("ping")) {
         p.addChatMessage("Your ping is " + p.ping + " ms.");
      } else if (cmd.equals("me")) {
         me(p, server, args);
      } else if (cmd.equals("rules")) {
         Texts.send(p, Texts.rules(), "No rules have been set on this server.");
      } else if (cmd.equals("motd")) {
         Texts.send(p, Texts.motd(), "No message of the day has been set.");
      } else if (cmd.equals("seen")) {
         seen(p, server, args);
      } else if (cmd.equals("mail")) {
         mail(p, server, args);
      } else if (cmd.equals("ignore")) {
         ignore(p, args);
      } else if (cmd.equals("helpop")) {
         helpop(p, server, args);
      } else if (cmd.equals("suicide")) {
         p.setHealth(0);
      } else if (cmd.equals("tphere")) {
         tphere(p, server, args);
      } else if (cmd.equals("feed")) {
         feed(p, server, args);
      } else if (cmd.equals("repair")) {
         repair(p);
      } else if (cmd.equals("burn")) {
         burn(p, server, args);
      } else if (cmd.equals("smite")) {
         smite(p, server, args);
      } else if (cmd.equals("top")) {
         top(p);
      } else if (cmd.equals("broadcast")) {
         broadcast(p, server, args);
      }
      return true;
   }

   // ---- player ----

   private static void afk(EntityPlayerMP p, MinecraftServer server) {
      boolean nowAfk = State.toggleAfk(p.getName());
      all(server, p.getName() + (nowAfk ? " is now AFK." : " is no longer AFK."));
   }

   private static void near(EntityPlayerMP p, String[] args) {
      int radius = 100;
      if (args.length > 1) {
         try {
            radius = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            p.addChatMessage("'" + args[1] + "' is not a distance.");
            return;
         }
      }
      if (radius < 1) {
         radius = 1;
      }

      List<String> found = new ArrayList<String>();
      // This world only. Someone 40 blocks away in the Crimson is not "nearby".
      for (int i = 0; i < p.world.players.size(); i++) {
         Player other = p.world.players.get(i);
         if (other == p) {
            continue;
         }
         double dx = other.posX - p.posX;
         double dy = other.posY - p.posY;
         double dz = other.posZ - p.posZ;
         double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (dist <= radius) {
            found.add(other.username + " (" + (int)dist + "m)");
         }
      }

      if (found.isEmpty()) {
         p.addChatMessage("Nobody else within " + radius + " blocks.");
         return;
      }
      p.addChatMessage("Within " + radius + " blocks: " + joinList(found));
   }

   private static void me(EntityPlayerMP p, MinecraftServer server, String[] args) {
      String action = Text.clean(Text.join(args, 1), 100);
      if (action.length() == 0) {
         p.addChatMessage("Usage: /me <what you are doing>");
         return;
      }
      all(server, "* " + p.getName() + " " + action);
   }

   private static void seen(EntityPlayerMP p, MinecraftServer server, String[] args) {
      if (args.length < 2) {
         p.addChatMessage("Usage: /seen <player>");
         return;
      }
      String target = Text.clean(args[1], 32);

      EntityPlayerMP online = find(server, target);
      if (online != null) {
         p.addChatMessage(online.getName() + " is online now"
               + (State.isAfk(online.getName()) ? " (AFK)." : "."));
         return;
      }

      long when = State.lastSeen(target);
      if (when == 0L) {
         p.addChatMessage("No record of " + target + " on this server.");
         return;
      }
      p.addChatMessage(target + " was last seen " + Ago.since(when) + " ago.");
   }

   private static void mail(EntityPlayerMP p, MinecraftServer server, String[] args) {
      String sub = args.length > 1 ? args[1].toLowerCase() : "read";

      if (sub.equals("read")) {
         List<String> box = State.mailFor(p.getName());
         if (box.isEmpty()) {
            p.addChatMessage("No mail.");
            return;
         }
         p.addChatMessage("--- mail (" + box.size() + ") ---");
         for (int i = 0; i < box.size(); i++) {
            p.addChatMessage(box.get(i));
         }
         p.addChatMessage("/mail clear when you are done.");
      } else if (sub.equals("clear")) {
         State.clearMail(p.getName());
         p.addChatMessage("Mail cleared.");
      } else if (sub.equals("send")) {
         if (args.length < 4) {
            p.addChatMessage("Usage: /mail send <player> <message>");
            return;
         }
         String to = Text.clean(args[2], 32);
         String body = Text.clean(Text.join(args, 3), 160);
         if (body.length() == 0) {
            p.addChatMessage("That message was empty.");
            return;
         }
         // The recipient's own ignore list applies to mail as well as to /msg -- otherwise
         // ignoring someone just moves their messages into your mailbox.
         if (State.ignores(to, p.getName())) {
            p.addChatMessage("Sent.");   // deliberately indistinguishable from success
            return;
         }
         State.addMail(to, "From " + p.getName() + ": " + body);

         EntityPlayerMP online = find(server, to);
         if (online != null) {
            online.addChatMessage("You have new mail from " + p.getName() + ". /mail read");
         }
         p.addChatMessage("Sent.");
      } else {
         p.addChatMessage("/mail read | /mail send <player> <message> | /mail clear");
      }
   }

   private static void ignore(EntityPlayerMP p, String[] args) {
      if (args.length < 2) {
         List<String> list = State.ignoreList(p.getName());
         p.addChatMessage(list.isEmpty()
               ? "You are not ignoring anyone."
               : "Ignoring: " + joinList(list));
         return;
      }
      String target = Text.clean(args[1], 32);
      if (target.equalsIgnoreCase(p.getName())) {
         p.addChatMessage("You cannot ignore yourself.");
         return;
      }
      boolean nowIgnoring = State.toggleIgnore(p.getName(), target);
      p.addChatMessage(nowIgnoring
            ? "Ignoring " + target + ". /ignore " + target + " again to undo."
            : "No longer ignoring " + target + ".");
   }

   private static void helpop(EntityPlayerMP p, MinecraftServer server, String[] args) {
      String message = Text.clean(Text.join(args, 1), 160);
      if (message.length() == 0) {
         p.addChatMessage("Usage: /helpop <what you need help with>");
         return;
      }
      server.configManager.sendMessageToOps("[helpop] " + p.getName() + ": " + message);
      System.out.println("[helpop] " + p.getName() + ": " + message);
      p.addChatMessage("Sent to staff. Someone will get back to you.");
   }

   // ---- op ----

   private static void tphere(EntityPlayerMP p, MinecraftServer server, String[] args) {
      if (args.length < 2) {
         p.addChatMessage("Usage: /tphere <player>");
         return;
      }
      EntityPlayerMP target = find(server, args[1]);
      if (target == null) {
         p.addChatMessage("'" + args[1] + "' is not online.");
         return;
      }
      if (target.dimension != p.dimension) {
         // Moving someone between dimensions is a different operation entirely -- see the
         // slipgate handling in NetServerHandler -- and doing it with setPosition strands them.
         p.addChatMessage(target.getName() + " is in another dimension. Use /dimension first.");
         return;
      }
      target.teleportAndNotify(p.posX, p.posY, p.posZ);
      target.addChatMessage("You were summoned by " + p.getName() + ".");
      p.addChatMessage("Brought " + target.getName() + " here.");
   }

   private static void feed(EntityPlayerMP p, MinecraftServer server, String[] args) {
      EntityPlayerMP target = args.length > 1 ? find(server, args[1]) : p;
      if (target == null) {
         p.addChatMessage("'" + args[1] + "' is not online.");
         return;
      }
      target.getFoodStats().setHunger(20);
      target.getFoodStats().setSaturation(5.0F);
      target.addChatMessage("You have been fed.");
      if (target != p) {
         p.addChatMessage("Fed " + target.getName() + ".");
      }
   }

   private static void repair(EntityPlayerMP p) {
      ItemStack held = p.inventory.getCurrentItem();
      if (held == null) {
         p.addChatMessage("You are not holding anything.");
         return;
      }
      if (held.getMaxDamage() <= 0) {
         p.addChatMessage("That item cannot be damaged.");
         return;
      }
      if (held.itemDmg == 0) {
         p.addChatMessage("That is already in perfect condition.");
         return;
      }
      held.setItemDamage(0);
      p.addChatMessage("Repaired.");
   }

   private static void burn(EntityPlayerMP p, MinecraftServer server, String[] args) {
      if (args.length < 2) {
         p.addChatMessage("Usage: /burn <player> [seconds]");
         return;
      }
      EntityPlayerMP target = find(server, args[1]);
      if (target == null) {
         p.addChatMessage("'" + args[1] + "' is not online.");
         return;
      }
      int seconds = 5;
      if (args.length > 2) {
         try {
            seconds = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            p.addChatMessage("'" + args[2] + "' is not a number of seconds.");
            return;
         }
      }
      if (seconds < 1) {
         seconds = 1;
      }
      target.fire = seconds * 20;   // the field counts ticks
      p.addChatMessage("Set " + target.getName() + " on fire for " + seconds + "s.");
   }

   private static void smite(EntityPlayerMP p, MinecraftServer server, String[] args) {
      if (args.length < 2) {
         p.addChatMessage("Usage: /smite <player>");
         return;
      }
      EntityPlayerMP target = find(server, args[1]);
      if (target == null) {
         p.addChatMessage("'" + args[1] + "' is not online.");
         return;
      }
      target.world.spawnEntity(
            new LightningBolt(target.world, target.posX, target.posY, target.posZ));
      p.addChatMessage("Smote " + target.getName() + ".");
   }

   private static void top(EntityPlayerMP p) {
      int x = (int)Math.floor(p.posX);
      int z = (int)Math.floor(p.posZ);
      int y = p.world.getTopSolidTile(x, z);
      if (y <= 0) {
         p.addChatMessage("Could not find the surface here.");
         return;
      }
      p.teleportAndNotify(p.posX, y + 1.0, p.posZ);
      p.addChatMessage("Teleported to y=" + (y + 1) + ".");
   }

   private static void broadcast(EntityPlayerMP p, MinecraftServer server, String[] args) {
      String message = Text.clean(Text.join(args, 1), 160);
      if (message.length() == 0) {
         p.addChatMessage("Usage: /broadcast <message>");
         return;
      }
      all(server, "[Server] " + message);
   }

   // ---- shared ----

   private static void all(MinecraftServer server, String message) {
      server.configManager.sendPacketToAll(new ChatPacket(message));
      System.out.println(message);
   }

   /** Case-insensitive, exact name. Prefix matching would let /smite hit the wrong person. */
   static EntityPlayerMP find(MinecraftServer server, String name) {
      for (int i = 0; i < server.configManager.playerEntities.size(); i++) {
         EntityPlayerMP p = server.configManager.playerEntities.get(i);
         if (p.getName().equalsIgnoreCase(name)) {
            return p;
         }
      }
      return null;
   }

   private static boolean contains(String[] arr, String value) {
      for (int i = 0; i < arr.length; i++) {
         if (arr[i].equals(value)) {
            return true;
         }
      }
      return false;
   }

   private static String joinList(List<String> parts) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parts.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }
         sb.append(parts.get(i));
      }
      return sb.toString();
   }
}
