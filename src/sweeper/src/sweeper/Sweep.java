package sweeper;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.game.entity.Entity;
import net.minecraft.game.entity.misc.DroppedItem;
import net.minecraft.game.entity.mob.Mob;
import net.minecraft.game.entity.player.Player;
import net.minecraft.game.item.ItemStack;
import net.minecraft.game.world.World;

/**
 * The sweep itself.
 *
 * Three passes, in increasing order of how much a player would mind:
 *
 *   1. merge  -- combines dropped stacks of the same item. Nobody loses anything; the ground
 *                still holds the same items, just in fewer entities. This is the pass that does
 *                most of the good, and it is the one that is on by default.
 *   2. expire -- deletes old dropped items. Off unless configured.
 *   3. trim   -- removes surplus mobs, furthest from a player first. Off unless configured.
 *
 * Everything runs on the server thread from a tick hook, so no locking is needed, but the
 * entity list is being iterated by the world at the same time: nothing here adds or removes
 * from world.entities directly. setDead() marks an entity and the world reaps it, which is the
 * same route despawning already takes.
 */
public final class Sweep {

   /** Counters for /sweep status. */
   public static long mergedTotal;
   public static long expiredTotal;
   public static long trimmedTotal;
   public static long lastRunAt;

   private Sweep() {
   }

   public static Result run(World world) {
      Result r = new Result();
      if (world == null || world.entities == null) {
         return r;
      }

      // Snapshot first. The passes call setDead(), and the world may mutate its own list while
      // we work; iterating a copy keeps this out of that argument entirely.
      List<Entity> all = new ArrayList<Entity>(world.entities);

      List<DroppedItem> items = new ArrayList<DroppedItem>();
      for (int i = 0; i < all.size(); i++) {
         Entity e = all.get(i);
         if (e instanceof DroppedItem && !e.isDead) {
            items.add((DroppedItem)e);
         }
      }

      if (Config.merge) {
         r.merged = merge(items);
      }
      if (Config.itemLifetimeSeconds > 0) {
         r.expired = expire(items);
      }
      if (Config.maxMobsPerWorld > 0) {
         r.trimmed = trim(world, all);
      }

      mergedTotal += r.merged;
      expiredTotal += r.expired;
      trimmedTotal += r.trimmed;
      lastRunAt = System.currentTimeMillis();
      return r;
   }

   /**
    * Combines stacks of the same item that are close together.
    *
    * O(n^2) over dropped items only. That is fine at the scale this runs at -- a laggy server
    * has hundreds of dropped items, not hundreds of thousands -- and the alternative, bucketing
    * by chunk, would miss pairs that straddle a chunk edge, which is exactly where cobblestone
    * from a tunnel piles up.
    */
   private static int merge(List<DroppedItem> items) {
      int merged = 0;
      double r2 = Config.mergeRadius * Config.mergeRadius;

      for (int i = 0; i < items.size(); i++) {
         DroppedItem a = items.get(i);
         if (a.isDead || a.item == null || !a.item.isStackable()) {
            continue;
         }

         for (int j = i + 1; j < items.size(); j++) {
            DroppedItem b = items.get(j);
            if (b.isDead || b.item == null) {
               continue;
            }
            if (a.item.itemID != b.item.itemID || a.item.getItemDamage() != b.item.getItemDamage()) {
               continue;
            }

            int limit = a.item.getStackLimit();
            int room = limit - a.item.stackSize;
            if (room <= 0) {
               break;   // a is full; nothing further can merge into it
            }

            double dx = a.posX - b.posX;
            double dy = a.posY - b.posY;
            double dz = a.posZ - b.posZ;
            if (dx * dx + dy * dy + dz * dz > r2) {
               continue;
            }

            int moved = Math.min(room, b.item.stackSize);
            a.item.stackSize += moved;
            b.item.stackSize -= moved;
            if (b.item.stackSize <= 0) {
               b.setDead();
               merged++;
            }
         }
      }
      return merged;
   }

   private static int expire(List<DroppedItem> items) {
      int removed = 0;
      // DroppedItem.age counts ticks, and the server runs at 20 per second.
      int maxAge = Config.itemLifetimeSeconds * 20;

      for (int i = 0; i < items.size(); i++) {
         DroppedItem it = items.get(i);
         if (it.isDead) {
            continue;
         }
         // pickupDelay is set on an item a player just threw or dropped on death; leaving those
         // alone means a death pile is never eaten out from under someone running back to it.
         if (it.pickupDelay > 0) {
            continue;
         }
         if (it.age > maxAge) {
            it.setDead();
            removed++;
         }
      }
      return removed;
   }

   /**
    * Removes surplus mobs, furthest from a player first.
    *
    * canDespawn() is the game's own answer to "may this be removed" -- it excludes bosses and
    * named mobs, and TameableMob overrides it for pets -- so this defers to it rather than
    * inventing a second rule that could disagree. Anything currently fighting a player is also
    * left alone; deleting the skeleton mid-fight would read as the server cheating.
    */
   private static int trim(World world, List<Entity> all) {
      List<Mob> candidates = new ArrayList<Mob>();
      int total = 0;

      for (int i = 0; i < all.size(); i++) {
         Entity e = all.get(i);
         if (!(e instanceof Mob) || e.isDead || e instanceof Player) {
            continue;
         }
         Mob m = (Mob)e;
         total++;
         if (!m.canDespawn() || m.boss) {
            continue;
         }
         if (m.attackTarget instanceof Player) {
            continue;
         }
         candidates.add(m);
      }

      int excess = total - Config.maxMobsPerWorld;
      if (excess <= 0 || candidates.isEmpty()) {
         return 0;
      }

      sortByDistanceFromPlayers(world, candidates);

      int removed = 0;
      for (int i = 0; i < candidates.size() && removed < excess; i++) {
         candidates.get(i).setDead();
         removed++;
      }
      return removed;
   }

   /** Furthest first. Insertion sort: the list is small and this avoids boxing a comparator. */
   private static void sortByDistanceFromPlayers(World world, List<Mob> mobs) {
      final double[] dist = new double[mobs.size()];
      for (int i = 0; i < mobs.size(); i++) {
         dist[i] = nearestPlayerDistanceSquared(world, mobs.get(i));
      }

      for (int i = 1; i < mobs.size(); i++) {
         Mob m = mobs.get(i);
         double d = dist[i];
         int j = i - 1;
         while (j >= 0 && dist[j] < d) {
            mobs.set(j + 1, mobs.get(j));
            dist[j + 1] = dist[j];
            j--;
         }
         mobs.set(j + 1, m);
         dist[j + 1] = d;
      }
   }

   private static double nearestPlayerDistanceSquared(World world, Entity e) {
      if (world.players == null || world.players.isEmpty()) {
         return Double.MAX_VALUE;
      }
      double best = Double.MAX_VALUE;
      for (int i = 0; i < world.players.size(); i++) {
         Player p = world.players.get(i);
         double dx = p.posX - e.posX;
         double dy = p.posY - e.posY;
         double dz = p.posZ - e.posZ;
         double d = dx * dx + dy * dy + dz * dz;
         if (d < best) {
            best = d;
         }
      }
      return best;
   }

   /** What one sweep did. */
   public static final class Result {
      public int merged;
      public int expired;
      public int trimmed;

      public boolean didAnything() {
         return this.merged > 0 || this.expired > 0 || this.trimmed > 0;
      }

      public String describe() {
         return "merged " + this.merged + ", removed " + this.expired
               + " old items, trimmed " + this.trimmed + " mobs";
      }
   }
}
