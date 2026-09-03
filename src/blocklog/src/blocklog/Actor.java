package blocklog;

/**
 * Who the server is currently acting on behalf of.
 *
 * This is the trick the whole logger turns on. Rather than decoding dig and place packets to
 * work out which block a player affected -- which means duplicating the placement rules for
 * doors, beds, slabs and everything else, and getting them subtly wrong -- the packet handlers
 * only announce *who* is acting, and the logging happens down in World.setBlock where the real
 * coordinates already are. One hook catches every block a click actually changed, including
 * the second half of a door and the bed foot.
 *
 * It also makes cancellation free. landclaim and worldprotect both refuse edits by cancelling
 * the packet handler before the world is ever touched, so a refused break simply never reaches
 * setBlock and never gets logged. Nothing here has to know those plugins exist.
 *
 * The flip side is the reason {@link #clear()} must always run: anything that changes blocks
 * while an actor is still set would be attributed to them. Every setter below is paired with a
 * finally-clear in the mixins.
 *
 * ThreadLocal rather than a static field. World mutation is all on the server thread today, so
 * a plain static would work, but the network layer has its own reader threads and getting that
 * wrong would mis-attribute edits to whoever acted last rather than failing visibly.
 */
public final class Actor {

   private static final ThreadLocal<String> CURRENT = new ThreadLocal<String>();

   /** Dimension of the world the current actor is acting in, for entries. */
   private static final ThreadLocal<Integer> DIM = new ThreadLocal<Integer>();

   private Actor() {
   }

   public static void set(String name, int dim) {
      CURRENT.set(name);
      DIM.set(Integer.valueOf(dim));
   }

   public static void clear() {
      CURRENT.remove();
      DIM.remove();
   }

   /** null when nothing should be logged right now. */
   public static String current() {
      return CURRENT.get();
   }

   public static int dim() {
      Integer d = DIM.get();
      return d == null ? 0 : d.intValue();
   }
}
