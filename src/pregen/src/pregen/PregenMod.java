package pregen;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Generates terrain ahead of time so players never have to wait for it.
 *
 * Generating a chunk costs roughly fifty times what reading an existing one costs -- measured
 * on this server, a 21x21 view square is about 65 seconds of work virgin and about 1.6 seconds
 * once it exists. The chunk loader now spreads that cost over many ticks instead of spending it
 * all in one, so the server no longer stalls; but a player walking into new ground still waits
 * for it. This is the other half: do the generating when nobody is waiting.
 */
@Mod("pregen")
public class PregenMod {

   public PregenMod(ModContext ctx) {
      ctx.onSetup(new Runnable() {
         public void run() {
            Config.load(new File("world", "pregen.properties"));
            Runner.load(new File("world", "pregen.state"));
            System.out.println("[pregen] ready -- " + Runner.summary());
         }
      });
   }
}
