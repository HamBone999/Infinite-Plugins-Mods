package chatbridge;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.network.packet.misc.ChatPacket;
import net.minecraft.server.MinecraftServer;

/**
 * Discord -> Minecraft, and the loop guard that keeps the two halves apart.
 *
 * The inbound poller runs on its own thread but must not touch the world from there, so
 * messages are parked in a queue and shown from the server thread on the next tick.
 *
 * {@link #suppressed()} is the important part. Outbound listens to every chat broadcast, so
 * showing a Discord message in game would immediately send it back to Discord, which would
 * come back on the next poll -- a loop that fills the channel in seconds. The flag marks
 * broadcasts this class caused so Outbound ignores exactly those.
 */
public final class Relay {

   private static final Queue<Pending> PENDING = new ArrayDeque<Pending>();
   private static final ThreadLocal<Boolean> SUPPRESS = new ThreadLocal<Boolean>();

   /** Bounded for the same reason as the outbound queue: a stall must not become an OOM. */
   private static final int MAX_PENDING = 200;

   /**
    * Nothing drains while the server is empty, so a line that has been waiting this long is
    * dropped instead of shown. Otherwise an hour of Discord chatter lands on whoever logs in
    * first, all at once and all out of date.
    */
   private static final long STALE_MILLIS = 60000L;

   private static final class Pending {
      final String line;
      final long at;

      Pending(String line) {
         this.line = line;
         this.at = System.currentTimeMillis();
      }
   }

   private Relay() {
   }

   /** Called from the poller thread. */
   public static synchronized void enqueue(String line) {
      if (PENDING.size() >= MAX_PENDING) {
         PENDING.poll();
      }
      PENDING.add(new Pending(line));
   }

   /** Called from the server thread, once a tick. */
   public static void flushTo(MinecraftServer server) {
      if (server == null || server.configManager == null) {
         return;
      }
      long now = System.currentTimeMillis();
      while (true) {
         Pending next;
         synchronized (Relay.class) {
            next = PENDING.poll();
         }
         if (next == null) {
            return;
         }
         if (now - next.at > STALE_MILLIS) {
            continue;
         }
         show(server, next.line);
      }
   }

   private static void show(MinecraftServer server, String line) {
      SUPPRESS.set(Boolean.TRUE);
      try {
         server.configManager.sendPacketToAll(new ChatPacket(line));
         System.out.println(line);
      } catch (Throwable t) {
         System.out.println("[chatbridge] could not show a Discord message: " + t);
      } finally {
         SUPPRESS.remove();
      }
   }

   /** True while a broadcast originated from Discord and must not be sent back. */
   public static boolean suppressed() {
      return Boolean.TRUE.equals(SUPPRESS.get());
   }
}
