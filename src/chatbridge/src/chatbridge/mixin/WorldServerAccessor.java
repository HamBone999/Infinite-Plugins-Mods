package chatbridge.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldServer keeps its MinecraftServer private; this reads it without reflection. */
@Mixin(WorldServer.class)
public interface WorldServerAccessor {

   @Accessor("mcServer")
   MinecraftServer getMcServer();
}
