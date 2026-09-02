package moderncmds;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Adds the commands this build is missing. Registers no blocks, items or entities,
 * so the infinite|registry table is unchanged -- safe to run server-side only.
 */
@Mod("moderncmds")
public class ModernCommandsMod {
   public ModernCommandsMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      PointStore.load(new File("world", "points.tsv"));
      System.out.println("[moderncmds] ready -- try /commands");
   }
}
