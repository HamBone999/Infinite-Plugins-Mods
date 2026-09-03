package basics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The order /help prints its sections in.
 *
 * HelpCategories hands back categories in the order they were first mentioned, which is the
 * order the addons happened to load. That is close to random: a section anyone can use, like
 * Discord, could land underneath three operator sections, and the list read as though the
 * operator commands were the main event.
 *
 * The rule here is the one a player needs: everything you can use, then everything you cannot.
 * Within each half, a small explicit order for the sections that exist today, then anything
 * new alphabetically -- so a future addon lands somewhere sensible without this needing an edit.
 */
public final class HelpOrder {

   /**
    * Sections that are operator-only but do not say so in the name. Everything else is judged
    * by its "(op)" suffix, which is the convention the addons already follow.
    */
   private static final Set<String> OP_BY_NAME = new HashSet<String>();

   static {
      OP_BY_NAME.add("anticheat");
   }

   /** Player-facing sections, best first. Anything not listed sorts after these, by name. */
   private static final String[] PLAYER_ORDER = {
      "Infinite", "Basics", "Land Claims", "Discord", "Permissions"
   };

   /** Operator sections, in the order an operator tends to reach for them. */
   private static final String[] OP_ORDER = {
      "Infinite (op)", "Basics (op)", "Server (op)", "Region editing (op)",
      "World Protect (op)", "Block Log (op)", "Housekeeping (op)", "Anticheat", "Discord (op)"
   };

   private HelpOrder() {
   }

   public static List<String> sort(List<String> categories) {
      List<String> out = new ArrayList<String>(categories);
      Collections.sort(out, new Comparator<String>() {
         public int compare(String a, String b) {
            boolean opA = isOp(a);
            boolean opB = isOp(b);
            if (opA != opB) {
               return opA ? 1 : -1;
            }

            int ra = rank(a, opA);
            int rb = rank(b, opB);
            if (ra != rb) {
               return ra < rb ? -1 : 1;
            }
            return a.compareToIgnoreCase(b);
         }
      });
      return out;
   }

   static boolean isOp(String category) {
      String lower = category.toLowerCase();
      return lower.endsWith("(op)") || OP_BY_NAME.contains(lower);
   }

   /** Position in the explicit table, or a value past the end so unknowns sort alphabetically. */
   private static int rank(String category, boolean op) {
      String[] table = op ? OP_ORDER : PLAYER_ORDER;
      for (int i = 0; i < table.length; i++) {
         if (table[i].equalsIgnoreCase(category)) {
            return i;
         }
      }
      return table.length;
   }
}
