package blocklog.mixin;

import blocklog.Actor;
import blocklog.Inspect;
import blocklog.LogCommands;
import blocklog.Perms;
import blocklog.Restore;
import blocklog.WandItem;
import net.minecraft.network.packet.player.DigPacket;
import net.minecraft.network.packet.player.PlacePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetServerHandler;
import net.minecraft.server.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks who is responsible while a dig or place is being handled, runs the wand, and runs /bl.
 *
 * These handlers do not log anything themselves -- {@link WorldLogMixin} does that, down where
 * the coordinates are. All this does is name the actor for the duration of the call.
 *
 * Priority 1500 puts this LAST in the HEAD chain, not first -- the applied order on this build
 * is anticheat, worldprotect, landclaim, then blocklog, confirmed in the transformed bytecode.
 * That is fine here: /bl is op-only and ops bypass both landclaim and worldprotect, so nothing
 * upstream cancels an operator's wand click before it arrives. It does mean this mixin cannot
 * answer for a click another addon already cancelled, which is why each addon restores its own
 * refused blocks rather than leaving it to a single shared hook.
 */
@Mixin(value = NetServerHandler.class, priority = 1500)
public abstract class ActorMixin {

   @Shadow public EntityPlayerMP playerEntity;
   @Shadow public MinecraftServer mcServer;

   private int blocklog$dim() {
      // Player.dimension, which is what the rest of the server keys worlds on -- see
      // MinecraftServer.getWorldManager(int) -- so a rollback resolves the same world back.
      return this.playerEntity.dimension;
   }

   private boolean blocklog$isOp() {
      return this.mcServer.configManager.isOp(this.playerEntity.getName().toLowerCase());
   }

   /** Whether this player may read block history -- the same node /bl wand is granted by. */
   private boolean blocklog$mayInspect() {
      return Perms.may(this.playerEntity, this.mcServer, "bl.wand");
   }

   /**
    * What a click does, given what the player is holding.
    *
    * Returns true when the click has been dealt with and must not reach the game.
    *
    * The wand is handled in two parts on purpose. Blocking the click is unconditional: the wand
    * is a genuine bedrock stack, so if one is ever dropped and picked up by an ordinary player,
    * letting the place through would hand them bedrock. Showing the history is separately gated
    * on op, because the block log is not public. A non-op holding a stray wand therefore finds
    * an item that does nothing at all, which is the right outcome for both problems.
    */
   private boolean blocklog$handleClick(int x, int y, int z) {
      if (WandItem.isHeld(this.playerEntity)) {
         if (this.blocklog$mayInspect()) {
            Inspect.report(this.playerEntity, this.blocklog$dim(), x, y, z);
         } else {
            this.playerEntity.addChatMessage("That is not yours to use.");
         }
         Restore.block(this.playerEntity, x, y, z);
         return true;
      }

      if (Inspect.isOn(this.playerEntity.getName())) {
         Inspect.report(this.playerEntity, this.blocklog$dim(), x, y, z);
         Restore.block(this.playerEntity, x, y, z);
         return true;
      }
      return false;
   }

   @Inject(method = "handleBlockDig", at = @At("HEAD"), cancellable = true)
   private void blocklog$digStart(DigPacket packet, CallbackInfo ci) {
      // status 0 is the start of a dig. The wand answers there and cancels, so the block is
      // never broken; answering on every status would print the same history three times.
      if (packet.status == 0
            && blocklog$handleClick(packet.xPosition, packet.yPosition, packet.zPosition)) {
         ci.cancel();
         return;
      }
      // The server jar's own // wand consumes a status 0 click inside the method body, after
      // every HEAD callback has run, and returns before reaching the code that would resend the
      // block. This mixin runs last in the chain, so it is the one place that can cover it.
      if (packet.status == 0 && blocklog$jarWandHeld()) {
         Restore.block(this.playerEntity, packet.xPosition, packet.yPosition, packet.zPosition);
      }

      Actor.set(this.playerEntity.getName(), blocklog$dim());
   }

   /**
    * Whether WandHook is about to consume this click.
    *
    * Mirrors WandHook.onDig's own condition -- operator, wand mode on, wand in hand -- using
    * only its public API, so this cannot claim a click the jar will not actually take.
    */
   private boolean blocklog$jarWandHeld() {
      try {
         if (!this.blocklog$isOp()
               || !net.minecraft.commands.custom.WandHook.enabled(this.playerEntity)) {
            // Still op here on purpose: this mirrors WandHook.onDig's own condition, which is
            // an op check in the server jar. Loosening it would claim clicks the jar will not.
            return false;
         }
         int id = net.minecraft.commands.custom.WandHook.wandId();
         net.minecraft.game.item.ItemStack held = this.playerEntity.inventory.getCurrentItem();
         return id >= 0 && held != null && held.itemID == id;
      } catch (Throwable t) {
         return false;
      }
   }

   @Inject(method = "handleBlockDig", at = @At("RETURN"))
   private void blocklog$digEnd(DigPacket packet, CallbackInfo ci) {
      Actor.clear();
   }

   @Inject(method = "handlePlace", at = @At("HEAD"), cancellable = true)
   private void blocklog$placeStart(PlacePacket packet, CallbackInfo ci) {
      // xPosition -1 is "used the item in the air", which targets no block.
      if (packet.xPosition != -1
            && blocklog$handleClick(packet.xPosition, packet.yPosition, packet.zPosition)) {
         ci.cancel();
         return;
      }
      Actor.set(this.playerEntity.getName(), blocklog$dim());
   }

   @Inject(method = "handlePlace", at = @At("RETURN"))
   private void blocklog$placeEnd(PlacePacket packet, CallbackInfo ci) {
      Actor.clear();
   }

   /**
    * The backstop for a leaked actor.
    *
    * If another addon cancels a dig or place at HEAD after this mixin has already set the actor,
    * the paired RETURN injection never runs and the name would stay set. Clearing at the end of
    * the packet batch bounds that to a single handlePackets call -- and since one handler serves
    * one player, anything mis-attributed inside that window is attributed to the right person
    * anyway. This exists so it cannot outlive the tick.
    */
   @Inject(method = "handlePackets", at = @At("RETURN"))
   private void blocklog$endOfBatch(CallbackInfo ci) {
      Actor.clear();
   }

   @Inject(method = "handleSlashCommand", at = @At("HEAD"), cancellable = true)
   private void blocklog$commands(String command, CallbackInfo ci) {
      if (LogCommands.handle(this.playerEntity, this.mcServer, command)) {
         ci.cancel();
      }
   }
}
