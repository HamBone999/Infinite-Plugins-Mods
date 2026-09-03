package chatbridge;

/** Cleaning messages as they cross between two very different chat systems. */
public final class Text {

   private static final char SECTION = (char)167;

   private Text() {
   }

   /**
    * Drops Minecraft colour codes. Discord shows them as literal junk -- "§ahambone" -- and the
    * section sign survives a copy-paste into a bug report looking like file corruption.
    */
   public static String stripColors(String s) {
      if (s == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == SECTION) {
            i++;   // skip the code that follows
            continue;
         }
         sb.append(c);
      }
      return sb.toString();
   }

   /**
    * Escapes Discord markdown so a message reads as typed.
    *
    * Without this, "I found *loads* of iron" loses its asterisks and someone typing a row of
    * underscores accidentally italicises half the channel.
    */
   public static String escapeMarkdown(String s) {
      StringBuilder sb = new StringBuilder(s.length() + 8);
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '*' || c == '_' || c == '~' || c == '`' || c == '>' || c == '|' || c == '\\') {
            sb.append('\\');
         }
         sb.append(c);
      }
      return sb.toString();
   }

   /**
    * Makes a Discord message safe to show in Minecraft chat.
    *
    * Strips the section sign -- otherwise anyone in Discord could inject colour codes into the
    * game, and more importantly could forge a line that looks like it came from the server --
    * flattens newlines, drops control characters the client will not render, and truncates.
    * The vanilla client kicks on an over-long chat packet, so the length cap is not cosmetic.
    */
   public static String forMinecraft(String s, int max) {
      StringBuilder sb = new StringBuilder(Math.min(s.length(), max));
      for (int i = 0; i < s.length() && sb.length() < max; i++) {
         char c = s.charAt(i);
         if (c == '\n' || c == '\r' || c == '\t') {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
               sb.append(' ');
            }
            continue;
         }
         if (c < 32 || c == 127 || c == SECTION) {
            continue;
         }
         sb.append(c);
      }
      String out = sb.toString().trim();
      return out.length() > max ? out.substring(0, max) : out;
   }

   /** Discord rejects an empty webhook body and hard-caps content at 2000 characters. */
   public static String forDiscord(String s, int max) {
      String clean = escapeMarkdown(stripColors(s)).trim();
      if (clean.length() > max) {
         clean = clean.substring(0, max - 3) + "...";
      }
      return clean;
   }
}
