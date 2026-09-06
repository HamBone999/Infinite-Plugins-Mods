package teleportfix;

import java.util.HashMap;
import java.util.Map;

/**
 * A short per-player gate on teleporter use.
 *
 * Every hop tears down the chunks around you and loads a fresh set at the far end -- measured
 * at roughly 440 chunks each way. Clicking a pad repeatedly queues that faster than the server
 * can retire it: an 18-hop burst produced 7,900 chunk loads in 70 seconds and had the server
 * logging "Can't keep up" every six seconds throughout, which is where the arrival lag, the
 * "moved too quickly" kick and at least some of the blank world come from.
 *
 * A second between hops costs a player nothing -- the trip already takes longer than that to
 * settle -- and it removes the only way anyone has found to overload this server on purpose.
 */
public final class Cooldown {

   /** Long enough to stop a burst, short enough that deliberate use never notices it. */
   private static final long MILLIS = 1500L;

   private static final Map<String, Long> LAST = new HashMap<String, Long>();

   private Cooldown() {
   }

   /** Milliseconds still to wait, or 0 if the hop may go ahead. Records the use when it may. */
   public static synchronized long check(String player) {
      String key = player.toLowerCase();
      long now = System.currentTimeMillis();
      Long last = LAST.get(key);
      if (last != null) {
         long waited = now - last.longValue();
         if (waited < MILLIS) {
            return MILLIS - waited;
         }
      }
      LAST.put(key, Long.valueOf(now));
      return 0L;
   }

   public static synchronized void forget(String player) {
      LAST.remove(player.toLowerCase());
   }
}
