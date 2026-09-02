package hamfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * FIX 3 -- the per-IP connection throttle that locks legitimate players out.
 *
 * NetworkListener$1.run() keeps a HashMap<InetAddress,Long> of last-connect times and,
 * for any address that is not literally "127.0.0.1", closes the socket outright when a
 * new connection arrives within 5000 ms:
 *
 *     if (map.containsKey(addr) && !"127.0.0.1".equals(addr.getHostAddress())
 *         && System.currentTimeMillis() - map.get(addr) < 5000L) {
 *         map.put(addr, System.currentTimeMillis());   // <-- RE-ARMS the window
 *         socket.close();                              // <-- instant FIN, then RST
 *     }
 *
 * The re-arm on the rejection path is the real defect: every retry inside the window
 * pushes the deadline forward another 5 seconds, so a client that auto-retries every
 * 2-3 seconds can never get in. Nothing is logged, because this happens in the accept
 * loop before NetLoginHandler exists. The 127.0.0.1 exemption is why every local probe
 * succeeds while the actual player is locked out.
 *
 * Dropping the constant to 500 ms keeps a floor against genuine connection floods while
 * putting any human-speed retry outside the window even with the re-arm still present.
 */
@Mixin(targets = "net.minecraft.server.network.NetworkListener$1")
public class ConnThrottleMixin {

   @ModifyConstant(method = "run", constant = @Constant(longValue = 5000L))
   private long hamfix$shorterThrottle(long original) {
      return 500L;
   }
}
