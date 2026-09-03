package blocklog;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed /bl filter.
 *
 * The syntax is CoreProtect's -- u:name t:2h r:20 a:block -- on purpose. Anyone who has
 * administered a Bukkit server already knows it, and the alternative is inventing a second
 * syntax that does the same job and has to be looked up every time.
 */
public final class Query {

   /** Lower-case names; empty means any actor. */
   public final List<String> actors = new ArrayList<String>();

   /** Epoch seconds; entries at or after this. 0 means no lower bound. */
   public long since;

   /** Centre and radius for the area filter. radius < 0 means the whole world. */
   public int radius = -1;
   public int cx;
   public int cy;
   public int cz;
   public int dim;
   public boolean dimSet;

   /** null = both, TRUE = only breaks, FALSE = only places. */
   public Boolean onlyBreaks;

   /** Set when parsing found something it did not understand. */
   public String error;

   public boolean matches(Entry e) {
      if (this.since > 0L && e.time < this.since) {
         return false;
      }
      if (this.dimSet && e.dim != this.dim) {
         return false;
      }
      if (!this.actors.isEmpty() && !this.actors.contains(e.actor.toLowerCase())) {
         return false;
      }
      if (this.onlyBreaks != null) {
         if (this.onlyBreaks.booleanValue() ? !e.isBreak() : !e.isPlace()) {
            return false;
         }
      }
      if (this.radius >= 0) {
         // A cube, not a sphere. It is what CoreProtect does, it is cheaper, and "everything
         // within 20 blocks" is understood as a box by everyone who types it.
         if (Math.abs(e.x - this.cx) > this.radius
               || Math.abs(e.z - this.cz) > this.radius
               || Math.abs(e.y - this.cy) > this.radius) {
            return false;
         }
      }
      return true;
   }

   /**
    * Parses the u:/t:/r:/a: arguments. Anything unrecognised sets {@link #error} rather than
    * being ignored: a typo'd filter that silently matches everything is how you roll back a
    * week of someone else's build.
    */
   public static Query parse(String[] args, int from, int dim, int x, int y, int z) {
      Query q = new Query();
      q.cx = x;
      q.cy = y;
      q.cz = z;
      q.dim = dim;
      q.dimSet = true;

      for (int i = from; i < args.length; i++) {
         String a = args[i];
         String lower = a.toLowerCase();

         if (lower.startsWith("u:")) {
            String v = a.substring(2);
            for (String part : v.split(",")) {
               if (part.trim().length() > 0) {
                  q.actors.add(part.trim().toLowerCase());
               }
            }
         } else if (lower.startsWith("t:")) {
            long secs = duration(lower.substring(2));
            if (secs < 0L) {
               q.error = "Could not read the time '" + a.substring(2) + "'. Try t:2h, t:30m or t:3d.";
               return q;
            }
            q.since = System.currentTimeMillis() / 1000L - secs;
         } else if (lower.startsWith("r:")) {
            try {
               q.radius = Integer.parseInt(lower.substring(2));
            } catch (NumberFormatException e) {
               q.error = "Could not read the radius '" + a.substring(2) + "'.";
               return q;
            }
            if (q.radius < 0) {
               q.error = "The radius cannot be negative.";
               return q;
            }
         } else if (lower.startsWith("a:")) {
            String v = lower.substring(2);
            if (v.equals("block") || v.equals("blocks")) {
               q.onlyBreaks = null;
            } else if (v.equals("break") || v.equals("broke") || v.equals("remove")) {
               q.onlyBreaks = Boolean.TRUE;
            } else if (v.equals("place") || v.equals("placed") || v.equals("add")) {
               q.onlyBreaks = Boolean.FALSE;
            } else {
               q.error = "Unknown action '" + v + "'. Use a:break, a:place or a:block.";
               return q;
            }
         } else if (lower.equals("global") || lower.equals("g")) {
            // Escape hatch for "this player, everywhere", which the dimension filter otherwise
            // blocks. Deliberately wordy rather than a bare flag character.
            q.dimSet = false;
            q.radius = -1;
         } else {
            q.error = "Unknown filter '" + a + "'. Use u:name t:2h r:20 a:break.";
            return q;
         }
      }
      return q;
   }

   /** "2h" -> 7200. Returns -1 if it cannot be read. Accepts a bare number as seconds. */
   public static long duration(String s) {
      if (s.length() == 0) {
         return -1L;
      }

      long total = 0L;
      long n = 0L;
      boolean sawDigit = false;
      boolean sawUnit = false;

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c >= '0' && c <= '9') {
            n = n * 10L + (c - '0');
            sawDigit = true;
            continue;
         }
         if (!sawDigit) {
            return -1L;
         }

         long mult;
         switch (c) {
            case 's': mult = 1L; break;
            case 'm': mult = 60L; break;
            case 'h': mult = 3600L; break;
            case 'd': mult = 86400L; break;
            case 'w': mult = 604800L; break;
            default: return -1L;
         }
         total += n * mult;
         n = 0L;
         sawDigit = false;
         sawUnit = true;
      }

      if (sawDigit) {
         // Trailing digits with no unit: seconds, so t:90 works.
         total += n;
      } else if (!sawUnit) {
         return -1L;
      }
      return total;
   }

   /** How the filter reads back to the player, so a listing says what it actually searched. */
   public String describe() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.actors.isEmpty() ? "anyone" : join(this.actors));
      if (this.since > 0L) {
         sb.append(", last ").append(Times.ago(this.since));
      }
      if (this.radius >= 0) {
         sb.append(", within ").append(this.radius).append(" blocks");
      } else {
         sb.append(", anywhere");
      }
      if (this.onlyBreaks != null) {
         sb.append(this.onlyBreaks.booleanValue() ? ", breaks only" : ", places only");
      }
      return sb.toString();
   }

   private static String join(List<String> parts) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parts.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }
         sb.append(parts.get(i));
      }
      return sb.toString();
   }
}
