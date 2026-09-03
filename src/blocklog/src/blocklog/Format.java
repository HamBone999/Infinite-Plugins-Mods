package blocklog;

/** One history line. Kept in one place so inspect, lookup and rollback all read the same. */
public final class Format {

   private Format() {
   }

   /**
    * "12m ago - hambone broke stone" / "3.1h ago - hambone placed torch".
    *
    * A change that is neither a plain break nor a plain place -- one block replaced by another,
    * which is what a bucket or a bonemeal does -- reads as "replaced X with Y" rather than being
    * forced into one of the two, because on a rollback those are the ones you need to look at.
    */
   public static String line(Entry e) {
      String who = e.actor;
      String when = Times.ago(e.time);

      if (e.isBreak()) {
         return when + " ago - " + who + " broke " + BlockNames.of(e.oldId);
      }
      if (e.isPlace()) {
         return when + " ago - " + who + " placed " + BlockNames.of(e.newId);
      }
      return when + " ago - " + who + " replaced " + BlockNames.of(e.oldId)
            + " with " + BlockNames.of(e.newId);
   }

   /** The same, with coordinates, for listings that span more than one block. */
   public static String lineWithPos(Entry e) {
      return line(e) + " (" + e.x + "," + e.y + "," + e.z + ")";
   }
}
