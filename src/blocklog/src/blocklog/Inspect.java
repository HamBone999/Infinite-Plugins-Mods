package blocklog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * Inspect mode: with it on, clicking a block prints that block's history instead of touching it.
 *
 * This is the tool you actually use. "Who broke this" is a question about one block, and typing
 * a lookup with the right coordinates is slower and easier to get wrong than walking up to the
 * hole and hitting it.
 *
 * Both the left and right click are intercepted so a block can be inspected from below or from
 * a face you cannot reach, and so an inspecting admin cannot place a torch by accident.
 */
public final class Inspect {

   private static final Set<String> ON = new HashSet<String>();

   private Inspect() {
   }

   public static synchronized boolean isOn(String player) {
      return ON.contains(player.toLowerCase());
   }

   /** Returns the new state. */
   public static synchronized boolean toggle(String player) {
      String key = player.toLowerCase();
      if (ON.remove(key)) {
         return false;
      }
      ON.add(key);
      return true;
   }

   public static synchronized void off(String player) {
      ON.remove(player.toLowerCase());
   }

   /** Prints everything the log holds for one block, newest first. */
   public static void report(EntityPlayerMP p, int dim, int x, int y, int z) {
      Log.flush();

      Query q = new Query();
      q.dim = dim;
      q.dimSet = true;
      q.cx = x;
      q.cy = y;
      q.cz = z;
      q.radius = 0;

      List<Entry> found = Log.query(q);
      p.addChatMessage("--- " + x + ", " + y + ", " + z + " ---");
      if (found.isEmpty()) {
         p.addChatMessage("No logged changes here.");
         return;
      }

      int from = Math.max(0, found.size() - Config.pageSize);
      for (int i = found.size() - 1; i >= from; i--) {
         p.addChatMessage(Format.line(found.get(i)));
      }
      if (from > 0) {
         p.addChatMessage("... and " + from + " older. /bl lookup r:0 for the rest.");
      }
   }
}
