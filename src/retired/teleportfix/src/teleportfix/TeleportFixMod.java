package teleportfix;

import infinite.api.Mod;
import infinite.api.ModContext;

/**
 * Teleporter rate limiting, plus temporary diagnostics for the "output is missing" report.
 *
 * Registers no blocks, items or entities, so this is server-side only and needs no client
 * update -- which is the whole reason the diagnostics live here rather than in the game jar.
 */
@Mod("teleportfix")
public class TeleportFixMod {

   public TeleportFixMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      System.out.println("[teleportfix] ready -- teleporter cooldown on, diagnostics "
            + (Diagnostics.enabled ? "ON (temporary)" : "off"));
   }
}
