package basics;

/** Message hygiene for anything a player typed that another player will see. */
public final class Text {

   private static final char SECTION = (char)167;

   private Text() {
   }

   /**
    * Strips colour codes and control characters from player input.
    *
    * The section sign is the important one: without this, /me, /mail and /helpop would each let
    * any player inject colour codes -- and, worse, forge a line that looks like it came from
    * the server or from another player.
    */
   public static String clean(String s, int max) {
      if (s == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder(Math.min(s.length(), max));
      for (int i = 0; i < s.length() && sb.length() < max; i++) {
         char c = s.charAt(i);
         if (c == SECTION || c < 32 || c == 127) {
            continue;
         }
         sb.append(c);
      }
      return sb.toString().trim();
   }

   /** Joins the tail of a command's arguments back into one message. */
   public static String join(String[] args, int from) {
      StringBuilder sb = new StringBuilder();
      for (int i = from; i < args.length; i++) {
         if (sb.length() > 0) {
            sb.append(' ');
         }
         sb.append(args[i]);
      }
      return sb.toString();
   }
}
