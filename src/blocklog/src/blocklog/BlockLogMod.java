package blocklog;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Block logging with rollback.
 *
 * Registers no blocks, items or entities, so the infinite|registry table is unchanged and this
 * is safe to run server-side only -- clients do not need it and do not need to match.
 *
 * setup() deliberately touches no game registry. An earlier addon looked up an item name here
 * and took the server down: the lookup pulled in BlockList before the game had finished
 * starting, the class initialiser failed, and every later use threw NoClassDefFoundError with a
 * stack trace that pointed at world loading rather than at the addon. Names are resolved lazily
 * in BlockNames instead, on the first command.
 */
@Mod("blocklog")
public class BlockLogMod {

   public BlockLogMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      Config.load(new File("world", "blocklog.properties"));
      Log.load(new File("world", "blocklog.log"));

      // The log is buffered, so a clean stop still needs to flush. Kill -9 loses the last few
      // seconds, which is the trade documented on Log.record.
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
         public void run() {
            Log.shutdown();
         }
      }, "blocklog-flush"));

      System.out.println("[blocklog] ready -- /bl wand, /bl lookup, /bl rollback");
   }
}
