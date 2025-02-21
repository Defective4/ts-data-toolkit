package io.github.defective4.tv.ttxconverter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;

public class Main {

    private static final char[] EN = {
            '£', '$', '@', '←', '½', '→', '↑', '#', '⌗', '—', '¼', '‖', '¾', '÷'
    };

    private static final char[] PL = {
            '#', 'ń', 'ą', 'Ƶ', 'Ś', 'Ł', 'ć', 'ó', 'ó', 'ę', 'ż', 'ś', 'ł', 'ź'
    };

    public static void main(String[] args) {
        String file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getName();
        if (args.length < 1) {
            System.err.println("Usage: java -jar " + file + " [directory]");
            System.exit(1);
            return;
        }
        File dir = new File(args[0]);
        if (!dir.isDirectory()) {
            System.err.println(args[0] + " is not a valid directory!");
            System.exit(1);
            return;
        }
        for (File f : dir.listFiles()) if (f.getName().endsWith(".html")) {
            System.err.println("Parsing file " + f.getName() + "...");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (BufferedReader reader = new BufferedReader(new FileReader(f));
                    PrintWriter bufferWriter = new PrintWriter(buffer)) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    for (int i = 0; i < EN.length; i++) line = line.replace(EN[i], PL[i]);
                    bufferWriter.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try (OutputStream os = Files.newOutputStream(f.toPath())) {
                os.write(buffer.toByteArray());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
