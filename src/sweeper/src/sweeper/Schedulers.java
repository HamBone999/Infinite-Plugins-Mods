package sweeper;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.game.world.World;

/**
 * One Scheduler per world.
 *
 * Keyed by identity rather than by dimension number: the same dimension id can be served by a
 * different World object after a reload, and an identity map lets the old one fall out with the
 * world instead of leaving a stale countdown attached to a live dimension.
 */
public final class Schedulers {

   private static final Map<World, Scheduler> BY_WORLD = new IdentityHashMap<World, Scheduler>();

   private Schedulers() {
   }

   public static void tick(World world) {
      if (world == null || world.isOnline) {
         return;   // isOnline means a client-side world; there is nothing to sweep there
      }
      Scheduler s = BY_WORLD.get(world);
      if (s == null) {
         s = new Scheduler();
         BY_WORLD.put(world, s);
      }
      s.tick(world);
   }
}
