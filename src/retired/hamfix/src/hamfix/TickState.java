package hamfix;

/**
 * Shared flag for "we are inside World.tickBlocks right now".
 *
 * Deliberately NOT on the mixin class: Mixin rejects non-private static methods in a
 * mixin ("contains non-private static method"), because they cannot be merged into the
 * target. Plain class, plain statics.
 *
 * ThreadLocal because only the Server Thread runs tickBlocks, but a client with an
 * integrated server shares the JVM.
 */
public final class TickState {

   private static final ThreadLocal<Boolean> IN_TICK = new ThreadLocal<Boolean>() {
      @Override protected Boolean initialValue() { return Boolean.FALSE; }
   };

   private TickState() { }

   public static boolean inBlockTick() { return IN_TICK.get().booleanValue(); }
   public static void enter()          { IN_TICK.set(Boolean.TRUE); }
   public static void exit()           { IN_TICK.set(Boolean.FALSE); }
}
