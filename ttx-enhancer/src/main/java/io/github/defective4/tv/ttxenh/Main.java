package io.github.defective4.tv.ttxenh;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Main {

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                String filename = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile())
                        .getName();
                System.err.println("Usage: java -jar " + filename + " [ttx html directory]");
                System.exit(1);
                return;
            }
            File ttxDir = new File(args[0]);
            if (!ttxDir.isDirectory()) {
                System.err.println("Directory " + args[0] + " does not exist!");
                System.exit(3);
                return;
            }

            File cssFile = new File(ttxDir, "ttx-enh.css");
            File jsFile = new File(ttxDir, "ttx-enh.js");

            String cssLink = "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + cssFile.getName() + "\"/>";
            String jsLink = "<script src=\"" + jsFile.getName() + "\"></script>";

            if (!cssFile.exists()) {
                System.err.println("Saving CSS file...");
                try (InputStream is = Main.class.getResourceAsStream("/ttx-enh.css")) {
                    Files.copy(is, cssFile.toPath());
                }
            }

            if (!jsFile.exists()) {
                System.err.println("Saving JS file...");
                try (InputStream is = Main.class.getResourceAsStream("/ttx-enh.js")) {
                    Files.copy(is, jsFile.toPath());
                }
            }

            boolean fill;
            System.err.print("Should we fill in missing pages in the teletext directory? [y/N]: ");
            fill = Character.toLowerCase(new InputStreamReader(System.in).read()) == 'y';

            List<File> htmlFiles = new ArrayList<>(Arrays.stream(ttxDir.listFiles()).filter(f -> {
                String name = f.getName();
                if (name.toLowerCase().endsWith(".html")) {
                    try {
                        int index = name.lastIndexOf('.');
                        Integer.parseInt(name.substring(0, index));
                        return true;
                    } catch (Exception e) {}
                }
                return false;
            }).toList());
            Collections.sort(htmlFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            if (htmlFiles.isEmpty()) {
                System.err.println("No HTML files found. Quiting.");
                System.exit(4);
                return;
            }

            if (fill) {
                System.err.println("Correcting file name cases...");
                List<File> toCorrect = new ArrayList<>();

                for (File f : htmlFiles) {
                    String name = f.getName();
                    if (!name.toLowerCase().equals(name)) {
                        toCorrect.add(f);
                        f.renameTo(new File(f.getParent(), name.toLowerCase()));
                    }
                }

                for (File f : toCorrect) {
                    htmlFiles.remove(f);
                    htmlFiles.add(new File(f.getParent(), f.getName().toLowerCase()));
                }

                System.err
                        .println(toCorrect.isEmpty() ? "All file names look OK"
                                : "Corrected file name cases for " + toCorrect.size() + " files!");

                StringBuilder template = new StringBuilder();
                try (InputStreamReader reader = new InputStreamReader(
                        Main.class.getResourceAsStream("/filling_template.html"), StandardCharsets.UTF_8)) {
                    while (true) {
                        int read = reader.read();
                        if (read == -1) break;
                        template.append((char) read);
                    }
                }

                System.err.println("Loading first available teletext document...");
                File first = htmlFiles.get(0);
                String firstName = first.getName();
                Document doc = Jsoup.parse(htmlFiles.get(0), "utf-8");
                Element row = doc.getElementsByClass("row").get(0);

                System.err.println("Filling missing pages...");
                int filled = 0;
                for (int i = 0; i <= 999; i++) {
                    File target = new File(ttxDir, i + ".html");
                    if (!target.exists()) {
                        String parsedTemplate = String
                                .format(template.toString(),
                                        row
                                                .html()
                                                .replace(firstName.substring(0, firstName.lastIndexOf('.')),
                                                        Integer.toString(i)));
                        try (Writer writer = new FileWriter(target, StandardCharsets.UTF_8)) {
                            writer.write(parsedTemplate);
                            filled++;
                        }
                        htmlFiles.add(target);
                    }
                }
                System.err.println("Filled " + filled + " missing pages");
            }

            int processed = 0;
            for (File f : htmlFiles) {
                boolean modified = false;
                if (!f.isFile()) {
                    System.err.println("File " + f.getName() + " disappeared. Skipping.");
                    continue;
                }
                System.err.println("Processing file " + f.getName() + "...");
                StringBuilder pageBuilder = new StringBuilder();
                try (Reader reader = new FileReader(f, StandardCharsets.UTF_8)) {
                    while (true) {
                        int read = reader.read();
                        if (read == -1) break;
                        pageBuilder.append((char) read);
                    }
                }
                String pageCnt = f.getName().substring(0, f.getName().indexOf('.'));
                String page = pageBuilder.toString();
                String oldPage = page;
                page = page.replaceAll("<body*.*>", "<body onload=\"attachLs(" + pageCnt + ")\">");
                if (!page.equals(oldPage)) {
                    modified = true;
                }
                int headLocation = page.indexOf("</head>");
                if (headLocation < 0) {
                    System.err.println("File " + f.getName() + " has no </head>. Skipped.");
                    continue;
                }

                if (page.contains("P" + pageCnt)) {
                    page = page
                            .replace("P" + pageCnt,
                                    "P<input type=\"text\" maxlength=\"3\" minlength=\"3\" class=\"page-input\" value=\""
                                            + pageCnt + "\"/>");
                    modified = true;
                } else {
                    System.err.println("Page index for file " + f.getName() + " is missing. Already modified?");
                }

                String preHead = page.substring(0, headLocation);
                String postHead = page.substring(headLocation);

                if (!page.contains(cssLink)) {
                    preHead += cssLink + "\n";
                    modified = true;
                }

                if (!page.contains(jsLink)) {
                    preHead += jsLink + "\n";
                    modified = true;
                }

                try (Writer writer = new FileWriter(f, StandardCharsets.UTF_8)) {
                    writer.write(preHead + postHead);
                }
                if (modified) processed++;
            }
            System.err.println("Processed " + processed + " teletext pages");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}
