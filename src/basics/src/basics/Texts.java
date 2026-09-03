package basics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.player.EntityPlayerMP;

/**
 * /rules and /motd, read from plain text files.
 *
 * Files rather than config keys so they can be edited without restarting and without escaping
 * anything -- one line of the file is one line in chat. Re-read on every use, which costs a
 * file read on a command nobody spams and means an edit takes effect immediately.
 */
public final class Texts {

   private static File rulesFile;
   private static File motdFile;

   private Texts() {
   }

   public static void init(File dir) {
      rulesFile = new File(dir, "rules.txt");
      motdFile = new File(dir, "motd.txt");
      seed(rulesFile,
         "# One rule per line. Blank lines and lines starting with # are skipped.",
         "Be decent to each other.",
         "Do not grief other people's builds.",
         "Ask before taking anything that is not yours.");
      seed(motdFile,
         "# Shown by /motd, and to players as they join.",
         "Welcome to Minecraft Infinite.",
         "/help for commands, /discord for the chat.");
   }

   public static List<String> rules() {
      return read(rulesFile);
   }

   public static List<String> motd() {
      return read(motdFile);
   }

   public static void send(EntityPlayerMP p, List<String> lines, String ifEmpty) {
      if (lines.isEmpty()) {
         p.addChatMessage(ifEmpty);
         return;
      }
      for (int i = 0; i < lines.size(); i++) {
         p.addChatMessage(lines.get(i));
      }
   }

   private static List<String> read(File f) {
      List<String> out = new ArrayList<String>();
      if (f == null || !f.exists()) {
         return out;
      }
      BufferedReader r = null;
      try {
         r = new BufferedReader(new FileReader(f));
         String line;
         while ((line = r.readLine()) != null && out.size() < 20) {
            String t = line.trim();
            if (t.length() == 0 || t.charAt(0) == '#') {
               continue;
            }
            out.add(Text.clean(t, 100));
         }
      } catch (IOException e) {
         System.out.println("[basics] could not read " + f + ": " + e);
      } finally {
         if (r != null) {
            try { r.close(); } catch (IOException ignored) { }
         }
      }
      return out;
   }

   private static void seed(File f, String... lines) {
      if (f.exists()) {
         return;
      }
      PrintWriter w = null;
      try {
         File parent = f.getParentFile();
         if (parent != null) {
            parent.mkdirs();
         }
         w = new PrintWriter(f);
         for (int i = 0; i < lines.length; i++) {
            w.println(lines[i]);
         }
      } catch (IOException e) {
         System.out.println("[basics] could not write " + f + ": " + e);
      } finally {
         if (w != null) {
            w.close();
         }
      }
   }
}
