package basics;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * The everyday commands a server is expected to have, plus the /help section ordering.
 *
 * Registers no blocks, items or entities, so this is server-side only and clients need no update.
 *
 * The commands that already exist in the server jar -- /home, /warp, /tpa, /msg, /back -- are
 * left alone. This fills the gaps around them rather than reimplementing any of them, because
 * two implementations of /tpa sharing no state would be worse than one.
 */
@Mod("basics")
public class BasicsMod {

   public BasicsMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      File world = new File("world");
      State.load(world);
      Texts.init(world);
      System.out.println("[basics] ready -- " + BasicCommands.playerCommands().length
            + " player commands, " + BasicCommands.opCommands().length + " op commands");
   }
}
