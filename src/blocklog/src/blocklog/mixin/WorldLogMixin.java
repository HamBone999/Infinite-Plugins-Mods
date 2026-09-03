package blocklog.mixin;

import blocklog.Actor;
import blocklog.Log;
import net.minecraft.game.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where block changes are actually recorded.
 *
 * setBlockAndMetadata and setBlock are the two primitives -- setBlockWithNotify and
 * setBlockAndMetadataWithNotify both delegate to them and then fire updates -- so hooking this
 * pair catches every change in the game without having to know what caused it.
 *
 * Nothing is logged unless {@link Actor} says who is responsible, which is set only around the
 * dig, place and explode handlers. That is what keeps the log to things people did: worldgen,
 * water spreading, grass growing, falling gravel and the // bulk editor all run with no actor
 * set and cost one ThreadLocal read each.
 *
 * Verified signatures on this build:
 *   World.setBlockAndMetadata(IIIII)Z
 *   World.setBlock(IIII)Z
 */
@Mixin(World.class)
public abstract class WorldLogMixin {

   @Inject(method = "setBlockAndMetadata", at = @At("HEAD"))
   private void blocklog$logSetWithMeta(int x, int y, int z, int id, int meta,
                                           CallbackInfoReturnable<Boolean> cir) {
      blocklog$log(x, y, z, id, meta);
   }

   @Inject(method = "setBlock", at = @At("HEAD"))
   private void blocklog$logSet(int x, int y, int z, int id, CallbackInfoReturnable<Boolean> cir) {
      // setBlockID does not carry metadata, so the new state is (id, 0).
      blocklog$log(x, y, z, id, 0);
   }

   /**
    * Recorded at HEAD, before the write, because that is the only point the previous state still
    * exists. The two conditions the real setter would reject on -- y outside the world, and a
    * write that changes nothing -- are re-checked here instead of reading the return value at
    * RETURN, which would be after the old block is gone.
    */
   private void blocklog$log(int x, int y, int z, int newId, int newMeta) {
      String actor = Actor.current();
      if (actor == null) {
         return;
      }

      World self = (World)(Object)this;
      if (y < 0 || y >= self.getWorldHeight()) {
         return;
      }

      int oldId = self.getBlockId(x, y, z);
      int oldMeta = self.getBlockMetadata(x, y, z);
      if (oldId == newId && oldMeta == newMeta) {
         return;
      }

      Log.record(System.currentTimeMillis() / 1000L, actor, Actor.dim(),
            x, y, z, oldId, oldMeta, newId, newMeta);
   }
}
