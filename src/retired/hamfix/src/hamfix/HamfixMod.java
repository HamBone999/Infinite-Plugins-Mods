package hamfix;

import infinite.api.Mod;
import infinite.api.ModContext;

/**
 * Server-side tick-stall fixes. Registers no blocks, items or entities, so the
 * infinite|registry table is byte-identical with and without this mod loaded.
 * That is what makes it safe to run server-only.
 */
@Mod("hamfix")
public class HamfixMod {
   public HamfixMod(ModContext ctx) {
      // Construction only. All behaviour lives in the mixins.
      System.out.println("[hamfix] tick guard armed");
   }
}
