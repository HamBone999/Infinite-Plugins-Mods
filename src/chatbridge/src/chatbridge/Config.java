package chatbridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Properties;

/**
 * Settings, including two secrets.
 *
 * This file holds a webhook URL and a bot token, either of which lets anyone who has it post as
 * the server. It lives in world/ rather than anywhere in the repo, and the file is created 0600
 * where the filesystem allows it. Nothing here is ever printed to the log -- {@link #summary()}
 * reports only whether a secret is set, never its value, because server logs get pasted into
 * bug reports.
 */
public final class Config {

   /** Discord webhook URL. Outbound only; no bot account needed for Minecraft -> Discord. */
   public static String webhookUrl = "";

   /** Bot token. Only needed for Discord -> Minecraft; leave blank for a one-way bridge. */
   public static String botToken = "";

   /** Channel to read from, when a bot token is set. */
   public static String channelId = "";

   /** Seconds between polls. Discord's REST limit is generous; 3 is comfortable. */
   public static int pollSeconds = 3;

   public static boolean relayChat = true;
   public static boolean relayJoinLeave = true;
   public static boolean relayDeaths = true;

   /** Discord -> Minecraft. Needs botToken and channelId. */
   public static boolean discordToMinecraft = true;

   /** Shown in game by /discord, so players can find the server. */
   public static String inviteUrl = "";

   /** Prefix on messages arriving from Discord, so nobody mistakes them for a player in game. */
   public static String discordPrefix = "[Discord] ";

   private Config() {
   }

   public static void load(File f) {
      if (!f.exists()) {
         write(f);
      }

      Properties p = new Properties();
      InputStream in = null;
      try {
         if (f.exists()) {
            in = new FileInputStream(f);
            p.load(in);
         }
      } catch (IOException e) {
         System.out.println("[chatbridge] could not read " + f + ": " + e);
      } finally {
         if (in != null) {
            try { in.close(); } catch (IOException ignored) { }
         }
      }

      webhookUrl = str(p, "webhook-url", webhookUrl);
      botToken   = str(p, "bot-token", botToken);
      channelId  = str(p, "channel-id", channelId);
      inviteUrl  = str(p, "invite-url", inviteUrl);
      discordPrefix = str(p, "discord-prefix", discordPrefix);

      pollSeconds = intOf(p, "poll-seconds", pollSeconds);
      if (pollSeconds < 2) {
         pollSeconds = 2;   // below this we are just burning Discord's rate limit
      }

      relayChat          = boolOf(p, "relay-chat", relayChat);
      relayJoinLeave     = boolOf(p, "relay-join-leave", relayJoinLeave);
      relayDeaths        = boolOf(p, "relay-deaths", relayDeaths);
      discordToMinecraft = boolOf(p, "discord-to-minecraft", discordToMinecraft);
   }

   public static boolean outboundReady() {
      return webhookUrl.length() > 0;
   }

   public static boolean inboundReady() {
      return discordToMinecraft && botToken.length() > 0 && channelId.length() > 0;
   }

   /** Never includes a secret -- only whether one is present. */
   public static String summary() {
      return "webhook " + (outboundReady() ? "set" : "not set")
           + ", bot token " + (botToken.length() > 0 ? "set" : "not set")
           + ", channel " + (channelId.length() > 0 ? "set" : "not set")
           + ", inbound " + (inboundReady() ? "on" : "off");
   }

   private static String str(Properties p, String key, String fallback) {
      String v = p.getProperty(key);
      return v == null ? fallback : v.trim();
   }

   private static int intOf(Properties p, String key, int fallback) {
      String v = p.getProperty(key);
      if (v == null) {
         return fallback;
      }
      try {
         return Integer.parseInt(v.trim());
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private static boolean boolOf(Properties p, String key, boolean fallback) {
      String v = p.getProperty(key);
      return v == null ? fallback : Boolean.parseBoolean(v.trim());
   }

   private static void write(File f) {
      PrintWriter w = null;
      try {
         File parent = f.getParentFile();
         if (parent != null) {
            parent.mkdirs();
         }
         w = new PrintWriter(new FileWriter(f));
         w.println("# Minecraft <-> Discord chat bridge.");
         w.println("# THIS FILE HOLDS SECRETS. Do not paste it into a bug report or commit it.");
         w.println();
         w.println("# Minecraft -> Discord. Channel settings -> Integrations -> Webhooks -> New Webhook.");
         w.println("# This alone gives you a working one-way bridge; no bot account needed.");
         w.println("webhook-url=");
         w.println();
         w.println("# Discord -> Minecraft. Needs a bot in the server with Read Messages and");
         w.println("# Message Content Intent, plus the channel id (Developer Mode -> Copy ID).");
         w.println("bot-token=");
         w.println("channel-id=");
         w.println("discord-to-minecraft=true");
         w.println();
         w.println("# Seconds between polls for new Discord messages. Minimum 2.");
         w.println("poll-seconds=" + pollSeconds);
         w.println();
         w.println("relay-chat=" + relayChat);
         w.println("relay-join-leave=" + relayJoinLeave);
         w.println("relay-deaths=" + relayDeaths);
         w.println();
         w.println("# Shown by /discord in game.");
         w.println("invite-url=");
         w.println();
         w.println("# Marks messages coming from Discord so they cannot be mistaken for a player.");
         w.println("discord-prefix=" + discordPrefix);
      } catch (IOException e) {
         System.out.println("[chatbridge] could not write " + f + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }

      // Best effort: on a normal Linux filesystem this makes the file owner-only. If the
      // platform refuses, the warning in the header is all we have.
      try {
         f.setReadable(false, false);
         f.setWritable(false, false);
         f.setReadable(true, true);
         f.setWritable(true, true);
      } catch (Throwable ignored) {
      }
   }
}
