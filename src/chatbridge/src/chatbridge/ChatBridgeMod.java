package chatbridge;

import infinite.api.Mod;
import infinite.api.ModContext;
import java.io.File;

/**
 * Minecraft <-> Discord chat.
 *
 * Registers no blocks, items or entities, so the infinite|registry table is unchanged and this
 * runs server-side only.
 *
 * Both directions are optional and independent. With only a webhook set you get a one-way feed
 * into Discord, which is the useful half and needs no bot account; adding a bot token and a
 * channel id turns on the return path.
 */
@Mod("chatbridge")
public class ChatBridgeMod {

   public ChatBridgeMod(ModContext ctx) {
      ctx.onSetup(this::setup);
   }

   private void setup() {
      File world = new File("world");
      Config.load(new File(world, "chatbridge.properties"));

      // The bot side works with or without a webhook, so these are set up before the
      // outbound check below returns early.
      Spool.init(world);
      Status.init(world);

      if (!Config.outboundReady()) {
         System.out.println("[chatbridge] no webhook set -- Minecraft chat will not reach Discord."
               + " Add webhook-url to world/chatbridge.properties and restart."
               + (Spool.ready() ? " The companion bot's messages will still arrive." : ""));
         return;
      }

      Outbound.start();
      Inbound.start();

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
         public void run() {
            Outbound.stop();
            Inbound.stop();
         }
      }, "chatbridge-stop"));

      System.out.println("[chatbridge] ready -- " + Config.summary());
   }
}
