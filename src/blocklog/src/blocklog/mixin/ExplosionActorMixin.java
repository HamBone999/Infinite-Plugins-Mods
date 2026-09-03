package blocklog.mixin;

import blocklog.Actor;
import blocklog.Config;
import net.minecraft.game.entity.Entity;
import net.minecraft.game.world.World;
import net.minecraft.game.world.util.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives craters an author.
 *
 * Without this a creeper hole is a hole: /bl lookup shows nothing, and the natural reading of
 * "no record" is that a player did it with the log off. Explosions are logged as "#creeper",
 * "#primedtnt" and so on, which reads clearly in a listing and can never collide with a real
 * player name.
 *
 * The hook is addParticles, not explode. explode() works out which blocks are in range and
 * fills destroyedBlocks; addParticles is what actually calls setBlockWithNotify on them, so it
 * is the call that needs an actor set around it. The name is a decompilation artefact.
 */
@Mixin(Explosion.class)
public abstract class ExplosionActorMixin {

   @Shadow private World world;
   @Shadow public Entity exploder;

   @Inject(method = "addParticles", at = @At("HEAD"))
   private void blocklog$blastStart(boolean flag, CallbackInfo ci) {
      if (!Config.logExplosions) {
         return;
      }
      int dim = this.world == null || this.world.currDim == null ? 0 : this.world.currDim.dimension;
      Actor.set(blocklog$name(), dim);
   }

   @Inject(method = "addParticles", at = @At("RETURN"))
   private void blocklog$blastEnd(boolean flag, CallbackInfo ci) {
      Actor.clear();
   }

   /** "#creeper" / "#primedtnt" / "#explosion" when nothing set it off. */
   private String blocklog$name() {
      if (this.exploder == null) {
         return "#explosion";
      }
      String cls = this.exploder.getClass().getSimpleName();
      return cls.length() == 0 ? "#explosion" : "#" + cls.toLowerCase();
   }
}
