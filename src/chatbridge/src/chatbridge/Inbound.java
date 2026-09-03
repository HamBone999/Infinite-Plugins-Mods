package chatbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Discord -> Minecraft, by polling the REST API.
 *
 * Polling rather than a gateway websocket. A real gateway connection means a websocket library,
 * heartbeats, reconnect handling and an intents handshake -- JDA is the usual answer and it is
 * a large dependency for a 1 GB server whose job here is to show a line of chat. A GET every
 * three seconds is a handful of requests a minute, well inside Discord's limits, and the worst
 * case is that a message shows up three seconds late.
 *
 * gson is used to parse the response because the server already ships it in libs/ (Apache 2.0),
 * so this adds no jar. Hand-rolling a JSON parser for untrusted input would be the wrong call.
 */
public final class Inbound {

   private static Thread worker;
   private static volatile boolean running;
   private static volatile boolean complained;

   /** Snowflake of the newest message already relayed, so a poll only returns what is new. */
   private static volatile String lastSeen;

   private Inbound() {
   }

   public static void start() {
      if (running || !Config.inboundReady()) {
         return;
      }
      running = true;
      worker = new Thread(new Runnable() {
         public void run() {
            loop();
         }
      }, "chatbridge-in");
      worker.setDaemon(true);
      worker.start();
   }

   public static void stop() {
      running = false;
      if (worker != null) {
         worker.interrupt();
      }
   }

   private static void loop() {
      // Start from now, not from the channel history: on a restart nobody wants the last fifty
      // Discord messages replayed into chat.
      try {
         lastSeen = newestId();
      } catch (Throwable t) {
         warnOnce("could not read the channel: " + t);
      }

      while (running) {
         try {
            Thread.sleep(Config.pollSeconds * 1000L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
         try {
            poll();
         } catch (Throwable t) {
            warnOnce("poll failed: " + t);
         }
      }
   }

   private static String newestId() throws Exception {
      JsonArray messages = get("/channels/" + Config.channelId + "/messages?limit=1");
      if (messages == null || messages.size() == 0) {
         return null;
      }
      return messages.get(0).getAsJsonObject().get("id").getAsString();
   }

   private static void poll() throws Exception {
      String url = "/channels/" + Config.channelId + "/messages?limit=25";
      if (lastSeen != null) {
         url += "&after=" + lastSeen;
      } else {
         url = "/channels/" + Config.channelId + "/messages?limit=1";
      }

      JsonArray messages = get(url);
      if (messages == null || messages.size() == 0) {
         return;
      }

      // Discord returns newest first; replay oldest first so the conversation reads in order.
      for (int i = messages.size() - 1; i >= 0; i--) {
         JsonObject m = messages.get(i).getAsJsonObject();
         String id = m.get("id").getAsString();
         lastSeen = id;

         JsonElement authorEl = m.get("author");
         if (authorEl == null || !authorEl.isJsonObject()) {
            continue;
         }
         JsonObject author = authorEl.getAsJsonObject();

         // Skip anything posted by a bot -- including our own webhook, which would otherwise
         // come straight back in as a Discord message and loop.
         if (author.has("bot") && author.get("bot").getAsBoolean()) {
            continue;
         }

         String name = author.has("global_name") && !author.get("global_name").isJsonNull()
               ? author.get("global_name").getAsString()
               : author.get("username").getAsString();

         String content = m.has("content") && !m.get("content").isJsonNull()
               ? m.get("content").getAsString() : "";

         // An attachment with no text is still worth showing; an empty line is not.
         if (content.trim().length() == 0) {
            if (m.has("attachments") && m.getAsJsonArray("attachments").size() > 0) {
               content = "(attachment)";
            } else {
               continue;
            }
         }

         String line = Config.discordPrefix
               + Text.forMinecraft(name, 32) + ": "
               + Text.forMinecraft(content, 160);
         Relay.enqueue(line);
      }
      complained = false;
   }

   private static JsonArray get(String path) throws Exception {
      HttpURLConnection conn = null;
      try {
         conn = (HttpURLConnection)new URL("https://discord.com/api/v10" + path).openConnection();
         conn.setRequestMethod("GET");
         conn.setRequestProperty("Authorization", "Bot " + Config.botToken);
         conn.setRequestProperty("User-Agent", "InfiniteChatBridge/0.1");
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);

         int code = conn.getResponseCode();
         if (code == 401 || code == 403) {
            // Not transient. Stop rather than retrying a bad token every three seconds forever.
            System.out.println("[chatbridge] Discord refused the bot token (HTTP " + code
                  + "). Inbound relay stopped -- check bot-token and channel-id, and that the"
                  + " bot can read that channel.");
            running = false;
            return null;
         }
         if (code == 429) {
            Thread.sleep(5000L);
            return null;
         }
         if (code < 200 || code >= 300) {
            warnOnce("Discord returned HTTP " + code);
            return null;
         }

         BufferedReader r = new BufferedReader(
               new InputStreamReader(conn.getInputStream(), "UTF-8"));
         try {
            JsonElement parsed = JsonParser.parseReader(r);
            return parsed != null && parsed.isJsonArray() ? parsed.getAsJsonArray() : null;
         } finally {
            r.close();
         }
      } finally {
         if (conn != null) {
            conn.disconnect();
         }
      }
   }

   private static void warnOnce(String what) {
      if (complained) {
         return;
      }
      complained = true;
      System.out.println("[chatbridge] " + what + " (further errors suppressed until it recovers)");
   }
}
