package landclaim;

import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Asks the perms addon whether a player holds a permission node, falling back to plain op.
 *
 * Reflective and optional, the same way worldprotect reaches landclaim. This addon has to work
 * whether perms is installed, renamed out of the way, or changed underneath it -- an addon that
 * refuses to start because an unrelated one moved a method is worse than one that quietly falls
 * back to operator-only.
 *
 * Nodes follow the convention perms already uses: the command name, optionally dotted with a
 * subcommand -- the same shape as the "whitelist.add" that ships in the default group file.
 * A group may hold any of:
 *
 *   *              everything
 *   bl             every /bl subcommand
 *   bl.*           the same, written explicitly
 *   bl.rollback    just that one
 *
 * Operators always pass, so a broken or missing permission file can never lock an operator out
 * of the tools they need to fix it.
 */
public final class Perms {

   private static Boolean present;
   private static Method has;

   private Perms() {
   }

   private static synchronized boolean available() {
      if (present == null) {
         try {
            Class<?> store = Class.forName("perms.PermStore");
            has = store.getMethod("has", String.class, String.class, boolean.class);
            present = Boolean.TRUE;
         } catch (Throwable t) {
            present = Boolean.FALSE;
         }
      }
      return present.booleanValue();
   }

   public static boolean isOp(EntityPlayerMP p, MinecraftServer server) {
      try {
         return server.configManager.isOp(p.getName().toLowerCase());
      } catch (Throwable t) {
         return false;
      }
   }

   /** True if the player may use this node. Operators always may. */
   public static boolean may(EntityPlayerMP p, MinecraftServer server, String node) {
      boolean op = isOp(p, server);
      if (op) {
         return true;
      }
      if (!available()) {
         return false;   // no perms addon: operator-only, as before
      }
      try {
         Object r = has.invoke(null, p.getName(), node, Boolean.FALSE);
         return r instanceof Boolean && ((Boolean)r).booleanValue();
      } catch (Throwable t) {
         return false;
      }
   }

   /** The message to show when {@link #may} said no, naming the node so it can be granted. */
   public static String denied(String node) {
      return "You do not have permission for that (" + node + ").";
   }
}
