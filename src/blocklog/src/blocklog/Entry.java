package blocklog;

/**
 * One block change.
 *
 * Both the before and after state are recorded, which is what makes rollback and restore the
 * same walk in opposite directions: rollback writes {@link #oldId}, restore writes {@link #newId}.
 * Storing only "what was placed" would leave a rollback unable to tell air from the grass it
 * replaced, and every undone build would come back sitting on a hole.
 *
 * Fields are package-visible and final. There are a lot of these in memory at once, so this is
 * deliberately a plain value holder with no accessors and no derived state.
 */
public final class Entry {

   /** Epoch seconds. Seconds, not millis: it halves nothing in memory but keeps the log readable. */
   public final long time;

   /**
    * Who did it. Interned through {@link Log}, so the thousands of entries one player generates
    * in a session share a single String rather than one copy each.
    *
    * Not always a player: explosions are logged as "#tnt" or "#creeper" so that a crater has an
    * author in the listing instead of appearing from nowhere.
    */
   public final String actor;

   public final int dim;
   public final int x;
   public final int y;
   public final int z;

   public final int oldId;
   public final int oldMeta;
   public final int newId;
   public final int newMeta;

   public Entry(long time, String actor, int dim, int x, int y, int z,
                int oldId, int oldMeta, int newId, int newMeta) {
      this.time = time;
      this.actor = actor;
      this.dim = dim;
      this.x = x;
      this.y = y;
      this.z = z;
      this.oldId = oldId;
      this.oldMeta = oldMeta;
      this.newId = newId;
      this.newMeta = newMeta;
   }

   /** True when this entry destroyed something -- a break, rather than a place on air. */
   public boolean isBreak() {
      return this.oldId != 0 && this.newId == 0;
   }

   public boolean isPlace() {
      return this.newId != 0 && this.oldId == 0;
   }

   /** Tab-separated, one line per entry. See {@link Log} for why this is not JSON. */
   public String serialize() {
      StringBuilder sb = new StringBuilder(64);
      sb.append(this.time).append('\t').append(this.actor).append('\t').append(this.dim)
        .append('\t').append(this.x).append('\t').append(this.y).append('\t').append(this.z)
        .append('\t').append(this.oldId).append('\t').append(this.oldMeta)
        .append('\t').append(this.newId).append('\t').append(this.newMeta);
      return sb.toString();
   }

   /** Returns null on a malformed line rather than throwing: a truncated log should still load. */
   public static Entry deserialize(String line) {
      String[] f = line.split("\t");
      if (f.length < 10) {
         return null;
      }
      try {
         return new Entry(
            Long.parseLong(f[0]), Log.intern(f[1]), Integer.parseInt(f[2]),
            Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
            Integer.parseInt(f[6]), Integer.parseInt(f[7]),
            Integer.parseInt(f[8]), Integer.parseInt(f[9]));
      } catch (NumberFormatException e) {
         return null;
      }
   }
}
