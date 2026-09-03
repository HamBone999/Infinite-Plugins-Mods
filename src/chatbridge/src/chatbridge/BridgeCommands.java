package chatbridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * /discord for everyone, /chatbridge for operators.
 *
 * Split in two because they answer different questions. A player wants the invite link; an
 * operator wants to know whether the bridge is actually connected, and neither wants the
 * other's output.
 */
public final class BridgeCommands {

   private BridgeCommands() {
   }

   public static boolean handle(EntityPlayerMP p, MinecraftServer server, String line) {
      String trimmed = line.startsWith("/") ? line.substring(1) : line;
      String[] args = trimmed.trim().split("\\s+");
      if (args.length == 0) {
         return false;
      }
      String cmd = args[0].toLowerCase();

      if (cmd.equals("discord")) {
         if (Config.inviteUrl.length() == 0) {
            p.addChatMessage("No Discord invite has been set on this server.");
         } else {
            p.addChatMessage("Discord: " + Config.inviteUrl);
         }
         return true;
      }

      if (!cmd.equals("chatbridge") && !cmd.equals("cb")) {
         return false;
      }

      String sub = args.length > 1 ? args[1].toLowerCase() : "status";

      String node = "chatbridge." + sub;
      if (!Perms.may(p, server, node)) {
         p.addChatMessage(Perms.denied(node));
         return true;
      }
      if (sub.equals("status")) {
         // Config.summary() reports presence, never values -- see the note on Config.
         p.addChatMessage("--- chat bridge ---");
         p.addChatMessage(Config.summary());
         p.addChatMessage("Relaying: chat " + on(Config.relayChat)
               + ", joins/leaves " + on(Config.relayJoinLeave)
               + ", deaths " + on(Config.relayDeaths));
         if (!Config.outboundReady()) {
            p.addChatMessage("Set webhook-url in world/chatbridge.properties, then restart.");
         }
      } else if (sub.equals("test")) {
         if (!Config.outboundReady()) {
            p.addChatMessage("No webhook is configured, so there is nothing to test.");
            return true;
         }
         Outbound.send("Bridge test from " + p.getName() + ".");
         p.addChatMessage("Sent. If it does not appear in Discord, check the console.");
      } else {
         p.addChatMessage("/chatbridge status | /chatbridge test");
      }
      return true;
   }

   private static String on(boolean b) {
      return b ? "on" : "off";
   }

   public static List<String> helpLines() {
      List<String> lines = new ArrayList<String>();
      lines.add("/discord -- the server's Discord invite");
      return lines;
   }

   public static List<String> opHelpLines() {
      List<String> lines = new ArrayList<String>();
      lines.add("/chatbridge status -- is the bridge connected");
      lines.add("/chatbridge test -- send a test message to Discord");
      return lines;
   }
}
