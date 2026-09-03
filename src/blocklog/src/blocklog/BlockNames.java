package blocklog;

/**
 * Block id to a readable name, for listings.
 *
 * Built lazily on the first /bl, never at setup. That is not a micro-optimisation: touching
 * ItemList during setup once took the whole server down. ItemList's static initialiser pulls in
 * BlockList, that failed while the game was still starting, and the class stayed poisoned with
 * NoClassDefFoundError for the rest of the run -- the server then died much later, during world
 * load, with a stack trace that pointed nowhere near the addon. Registry classes are only safe
 * to touch well after boot, and a command is the latest possible moment.
 *
 * Every lookup falls back to the raw id, so a name this cannot resolve degrades to "block 47"
 * rather than an exception in the middle of a rollback.
 */
public final class BlockNames {

   private static String[] names;
   private static boolean tried;

   private BlockNames() {
   }

   public static synchronized String of(int id) {
      if (id == 0) {
         return "air";
      }

      if (!tried) {
         tried = true;
         build();
      }

      if (names != null && id >= 0 && id < names.length && names[id] != null) {
         return names[id];
      }
      return "block " + id;
   }

   private static void build() {
      try {
         net.minecraft.game.block.Block[] blocks = net.minecraft.game.block.BlockList.blocks;
         names = new String[blocks.length];
         for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == null) {
               continue;
            }
            String n = blocks[i].name;
            if (n != null && n.length() > 0) {
               names[i] = n;
            }
         }
      } catch (Throwable t) {
         // Deliberately broad, and deliberately reported. If the registry is unavailable the
         // listing should say "block 47" rather than the command failing.
         System.out.println("[blocklog] block names unavailable, listings will use ids: " + t);
         names = null;
      }
   }
}
