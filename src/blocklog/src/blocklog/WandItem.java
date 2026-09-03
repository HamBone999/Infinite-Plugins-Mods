package blocklog;

import com.mojang.nbt.tag.special.CompoundTag;
import net.minecraft.game.item.ItemStack;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * The inspection wand: a piece of enchanted bedrock.
 *
 * Bedrock because it is unmistakable. Every other candidate is something a player might already
 * be holding for a real reason -- worldprotect took the gold hoe, land claims took the gold
 * shovel, the // editor took the stone axe -- and a wand that is also a tool means every click
 * does two things. Nobody is carrying bedrock by accident, and nobody can craft one, so a
 * bedrock block in a hand can only be this.
 *
 * The enchantment is what makes it visible: the glint says "this is not ordinary bedrock" at a
 * glance, and this build's wire format carries item NBT and a custom name (see
 * IOUtil.readItemStack), so both survive the trip to the client.
 *
 * Identification is by the NBT marker, not by the enchantment. A player could conceivably end
 * up holding enchanted bedrock some other way; they cannot end up holding one that carries this
 * tag unless it came from /bl wand.
 */
public final class WandItem {

   /** The NBT key that makes a stack the wand. */
   private static final String MARKER = "BlockLogWand";

   public static final String DISPLAY_NAME = "Block Log Wand";

   private static int bedrockId = Integer.MIN_VALUE;

   private WandItem() {
   }

   /**
    * Bedrock's id, resolved on first use.
    *
    * Lazily, and never at setup: reaching into BlockList while the game is still starting once
    * poisoned the class and took the server down long afterwards. See BlockNames for the full
    * story. A command is late enough to be safe.
    */
   public static synchronized int id() {
      if (bedrockId == Integer.MIN_VALUE) {
         bedrockId = -1;
         try {
            bedrockId = net.minecraft.game.block.BlockList.bedrock.id;
         } catch (Throwable t) {
            System.out.println("[blocklog] could not resolve bedrock, the wand is unavailable: " + t);
         }
      }
      return bedrockId;
   }

   /** Returns false if the block registry could not be read. */
   public static boolean give(EntityPlayerMP p) {
      int id = id();
      if (id < 0) {
         return false;
      }

      CompoundTag tag = new CompoundTag();
      tag.setBoolean(MARKER, true);

      ItemStack wand = new ItemStack(id, 1, 0, DISPLAY_NAME, tag);
      enchant(wand);
      return p.inventory.addItemToInventory(wand);
   }

   /**
    * Adds the glint.
    *
    * Cosmetic only -- nothing reads the enchantment back -- so a registry that will not load
    * costs the shine and nothing else, and the wand still works.
    */
   private static void enchant(ItemStack wand) {
      try {
         wand.addEnchantment(net.minecraft.game.item.enchantment.EnchantmentList.unbreaking, 1);
      } catch (Throwable t) {
         System.out.println("[blocklog] wand has no glint (enchantment registry unavailable): " + t);
      }
   }

   public static boolean isWand(ItemStack stack) {
      if (stack == null) {
         return false;
      }
      int id = id();
      if (id < 0 || stack.itemID != id) {
         return false;
      }
      try {
         CompoundTag tag = stack.customTag;
         return tag != null && tag.getBoolean(MARKER);
      } catch (Throwable t) {
         return false;
      }
   }

   public static boolean isHeld(EntityPlayerMP p) {
      return isWand(p.inventory.getCurrentItem());
   }
}
