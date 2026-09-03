package chatbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minecraft -> Discord, over a webhook.
 *
 * A webhook rather than a bot connection because outbound is the half that matters and a
 * webhook needs no gateway, no library and no bot account -- just a URL from the channel
 * settings. The bot token in Config is only for reading messages back.
 *
 * Everything goes through a queue drained by one daemon thread. A webhook POST is a network
 * round trip to Discord and occasionally a rate-limit wait; doing that on the server thread
 * would stall the tick every time somebody spoke.
 */
public final class Outbound {

   /**
    * Bounded on purpose. If Discord is unreachable the queue must not grow without limit --
    * this box has 3 GB of RAM, and a bridge falling behind should drop chat, not the server.
    */
   private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<String>(500);

   private static Thread worker;
   private static volatile boolean running;

   /** Set after a failure so the log gets one warning, not one per message. */
   private static volatile boolean complained;

   private static long droppedSinceWarning;

   private Outbound() {
   }

   public static void start() {
      if (running || !Config.outboundReady()) {
         return;
      }
      running = true;
      worker = new Thread(new Runnable() {
         public void run() {
            drain();
         }
      }, "chatbridge-out");
      worker.setDaemon(true);
      worker.start();
   }

   public static void stop() {
      running = false;
      if (worker != null) {
         worker.interrupt();
      }
   }

   /** Called from the server thread. Never blocks. */
   public static void send(String message) {
      if (!running) {
         return;
      }
      String clean = Text.forDiscord(message, 1900);
      if (clean.length() == 0) {
         return;
      }
      if (!QUEUE.offer(clean)) {
         droppedSinceWarning++;
         if (droppedSinceWarning == 1 || droppedSinceWarning % 100 == 0) {
            System.out.println("[chatbridge] outbound queue full, dropped "
                  + droppedSinceWarning + " messages -- is Discord reachable?");
         }
      }
   }

   private static void drain() {
      while (running) {
         String msg;
         try {
            msg = QUEUE.poll(1L, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
         if (msg == null) {
            continue;
         }
         post(msg);
      }
   }

   private static void post(String content) {
      HttpURLConnection conn = null;
      try {
         conn = (HttpURLConnection)new URL(Config.webhookUrl).openConnection();
         conn.setRequestMethod("POST");
         conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
         conn.setRequestProperty("User-Agent", "InfiniteChatBridge/0.1");
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);
         conn.setDoOutput(true);

         JsonObject body = new JsonObject();
         body.addProperty("content", content);

         // Nothing sent from Minecraft may ping anyone. Without this a player typing
         // "@everyone" in game notifies the whole Discord, which is a griefing tool.
         JsonObject allowed = new JsonObject();
         allowed.add("parse", new JsonArray());
         body.add("allowed_mentions", allowed);

         byte[] payload = body.toString().getBytes("UTF-8");
         conn.setFixedLengthStreamingMode(payload.length);
         OutputStream os = conn.getOutputStream();
         try {
            os.write(payload);
         } finally {
            os.close();
         }

         int code = conn.getResponseCode();
         if (code == 429) {
            // Rate limited. Back off rather than hammering; chat is not worth a ban.
            String retry = conn.getHeaderField("Retry-After");
            long wait = 2000L;
            if (retry != null) {
               try {
                  wait = (long)(Double.parseDouble(retry.trim()) * 1000.0);
               } catch (NumberFormatException ignored) {
               }
            }
            Thread.sleep(Math.max(500L, Math.min(wait, 30000L)));
            QUEUE.offer(content);
            return;
         }
         if (code < 200 || code >= 300) {
            warnOnce("Discord returned HTTP " + code + " for the webhook");
            return;
         }
         complained = false;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (Throwable t) {
         warnOnce("could not reach Discord: " + t);
      } finally {
         if (conn != null) {
            conn.disconnect();
         }
      }
   }

   /**
    * One warning per outage.
    *
    * A bridge that logs every failed send turns a Discord outage into gigabytes of server log,
    * which is its own incident.
    */
   private static void warnOnce(String what) {
      if (complained) {
         return;
      }
      complained = true;
      System.out.println("[chatbridge] " + what + " (further errors suppressed until it recovers)");
   }
}
