package blocklog;

/** Human time strings. "3.2h ago" is what you read in a listing; epoch seconds is what is stored. */
public final class Times {

   private Times() {
   }

   /** How long ago an epoch-second timestamp was, as "12m" / "3.2h" / "4.1d". */
   public static String ago(long epochSeconds) {
      long secs = System.currentTimeMillis() / 1000L - epochSeconds;
      if (secs < 0L) {
         secs = 0L;
      }
      if (secs < 60L) {
         return secs + "s";
      }
      if (secs < 3600L) {
         return (secs / 60L) + "m";
      }
      if (secs < 86400L) {
         return oneDecimal(secs, 3600L) + "h";
      }
      return oneDecimal(secs, 86400L) + "d";
   }

   /** Formats without String.format, which is slow enough to matter in a per-line loop. */
   private static String oneDecimal(long value, long unit) {
      long whole = value / unit;
      long tenths = (value % unit) * 10L / unit;
      return whole + "." + tenths;
   }
}
