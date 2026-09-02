package hamfix.mixin;

import hamfix.TickState;
import net.minecraft.game.world.chunk.Chunk;
import net.minecraft.server.world.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FIX 1 -- stop cascading worldgen inside the tick loop.
 *
 * Observed hot path (8 of 21 busy stack samples, 2026-08-23):
 *   World.tickBlocks(1823) -> World.getBlockId(372) -> World.getChunk(425)
 *     -> ChunkProviderServer.generateChunk(109) -> Long2ObjectOpenHashMap.get
 *
 * A random block tick reads a block id, the read lands in an unloaded chunk, and
 * the server generates the whole chunk synchronously inside the tick.
 *
 * Signatures verified against the shipped jar's constant pool:
 *   ChunkProviderServer.generateChunk(II)Lnet/minecraft/game/world/chunk/Chunk;
 *   ChunkProviderServer.dummyChunk : Lnet/minecraft/game/world/chunk/Chunk;
 */
@Mixin(ChunkProviderServer.class)
public abstract class ChunkGenGuardMixin {

   @Shadow private Chunk dummyChunk;

   @Inject(method = "generateChunk", at = @At("HEAD"), cancellable = true)
   private void hamfix$noGenerateDuringBlockTick(int cx, int cz, CallbackInfoReturnable<Chunk> cir) {
      if (TickState.inBlockTick()) {
         // Return the empty placeholder instead of generating terrain mid-tick.
         // Callers already tolerate this: it is what the vanilla chunkLoadOverride
         // path hands back when generation is suppressed.
         cir.setReturnValue(this.dummyChunk);
      }
   }
}
