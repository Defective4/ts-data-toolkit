package io.github.defective4.tv.t42parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class TeletextCommand {
    private TeletextCommand() {}

    public static void process(byte[] t42data, File targetDir) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder("teletext", "html", targetDir.toString(), "-").start();
        try (OutputStream os = proc.getOutputStream()) {
            os.write(t42data);
        }
        try (InputStreamReader reader = new InputStreamReader(proc.getErrorStream())) {
            while (true) {
                int c = reader.read();
                if (c == -1) break;
                System.err.print((char) c);
            }
        } catch (Exception e) {}
        if (proc.waitFor() != 0) throw new IOException("Couldn't decode teletext");
    }
}
