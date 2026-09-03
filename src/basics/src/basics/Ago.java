package basics;

/** "3.2h" / "4.1d". Shared by /seen and /mail so two commands do not disagree about time. */
public final class Ago {

   private Ago() {
   }

   public static String since(long epochSeconds) {
      long secs = System.currentTimeMillis() / 1000L - epochSeconds;
      if (secs < 0L) {
         secs = 0L;
      }
      if (secs < 60L) {
         return secs + " seconds";
      }
      if (secs < 3600L) {
         return (secs / 60L) + " minutes";
      }
      if (secs < 86400L) {
         return dec(secs, 3600L) + " hours";
      }
      return dec(secs, 86400L) + " days";
   }

   private static String dec(long value, long unit) {
      return (value / unit) + "." + ((value % unit) * 10L / unit);
   }
}
