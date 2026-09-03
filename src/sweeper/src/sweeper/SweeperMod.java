package sweeper;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Entity housekeeping: merges dropped stacks, and optionally expires old items and caps mobs.
 *
 * Registers no blocks, items or entities, so this is server-side only.
 *
 * Ships doing only the harmless half. Merging cuts the entity count without anyone losing an
 * item; deleting loot and culling mobs are both off until an operator decides the lag is worth
 * that trade, because the failure mode of a too-eager cleaner -- your death pile vanishing
 * while you run back to it -- is worse than the lag it was fixing.
 */
@Mod("sweeper")
public class SweeperMod {

   public SweeperMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      Config.load(new File("world", "sweeper.properties"));
      System.out.println("[sweeper] ready -- every " + Config.intervalSeconds + "s, merge "
            + (Config.merge ? "on" : "off")
            + ", item lifetime " + (Config.itemLifetimeSeconds > 0
                  ? Config.itemLifetimeSeconds + "s" : "off")
            + ", mob cap " + (Config.maxMobsPerWorld > 0
                  ? String.valueOf(Config.maxMobsPerWorld) : "off"));
   }
}
