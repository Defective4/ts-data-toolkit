package io.github.defective4.tv.epgreader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Main {

    private static final DateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    public static void main(String[] args) throws IOException {
        String file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getName();
        if (args.length < 3) {
            System.err.println("Usage: java -jar " + file + " [output dir] [locale] [input files...]");
            System.exit(3);
            return;
        }
        File outputDir = new File(args[0]);
        if (outputDir.isFile()) {
            System.err.println(outputDir + " already exists!");
            System.exit(1);
            return;
        }

        if (outputDir.isDirectory() && outputDir.list().length > 0) {
            System.err.println(outputDir + " already exists and is not empty!");
            System.exit(1);
            return;
        }

        String localeName = args[1].toLowerCase();
        Properties locale = new Properties();
        try (InputStream is = Main.class.getResourceAsStream("/lang/" + localeName + ".properties")) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    locale.load(reader);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Locale loc = new Locale.Builder().setLanguage(localeName.toUpperCase()).build();
        DateFormat dateFmt = new SimpleDateFormat(locale.getProperty("date_fmt", "dd MMM"), loc);
        DateFormat dateFmtExt = new SimpleDateFormat(locale.getProperty("date_fmt_ext", "dd MMMM"), loc);
        DateFormat dateFmtYear = new SimpleDateFormat(locale.getProperty("date_fmt_year", "dd.MM.yyyy"), loc);
        DateFormat fullDate = new SimpleDateFormat(locale.getProperty("date_fmt_year", "dd.MM.yyyy") + " hh:mm");

        List<File> inputFiles = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            File f = new File(args[i]);
            if (!f.isFile()) {
                System.err.println("File " + f + " is not a valid file!");
                System.exit(1);
                return;
            }
            inputFiles.add(f);
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            System.err.println("Couldn't create output directory!");
            System.exit(1);
            return;
        }

        System.err.println("Parsing files...");
        Map<String, List<FriendlyEvent>> channels = new LinkedHashMap<>();
        for (File f : inputFiles) {
            System.err.println("Parsing " + f + "...");
            try {
                channels.putAll(TSReader.readTransportStream(f));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
                return;
            }
        }

        System.err.println("Parsing HTML template...");
        StringBuilder viewerHTML = new StringBuilder();
        try (Reader reader = new InputStreamReader(Main.class.getResourceAsStream("/viewer.html.template"),
                StandardCharsets.UTF_8)) {
            while (true) {
                int read = reader.read();
                if (read == -1) break;
                viewerHTML.append((char) read);
            }
        }

        StringBuilder detailsHTML = new StringBuilder();
        try (Reader reader = new InputStreamReader(Main.class.getResourceAsStream("/details.html.template"),
                StandardCharsets.UTF_8)) {
            while (true) {
                int read = reader.read();
                if (read == -1) break;
                detailsHTML.append((char) read);
            }
        }

        Document doc = Jsoup.parse(viewerHTML.toString());
        System.err.println("Constructing HTML documents...");
        doc.getElementById("epg-title").html(locale.getProperty("epg_header", "TV Electronic Program Guide"));
        doc.getElementById("epg-date").html(dateFmtYear.format(new Date(System.currentTimeMillis())));
        Element channelsEl = doc.getElementById("channels");
        Element programsEl = doc.getElementById("programs");
        try (PrintWriter csvWriter = new PrintWriter(new File(outputDir, "epg.csv"), StandardCharsets.UTF_8)) {
            csvWriter.println("Channel;Event Name;Event ID;Start;End;Duration;Age rating;Genre");
            for (Map.Entry<String, List<FriendlyEvent>> entry : channels.entrySet()) {
                Element channel = channelsEl.appendElement("th");
                channel.html(entry.getKey());
                Element cardContainer = programsEl.appendElement("td").appendElement("div");
                cardContainer.addClass("program-cards");
                for (FriendlyEvent event : entry.getValue()) {
                    csvWriter
                            .println(String
                                    .format("%s;%s;%s;%s;%s;%s;%s;%s", entry.getKey(), event.name(), event.id(),
                                            fullDate.format(new Date(event.startTime())),
                                            fullDate.format(new Date(event.endTime())),
                                            Duration
                                                    .ofMillis(event.endTime() - event.startTime())
                                                    .toString()
                                                    .substring(2),
                                            event.ageRating() == -1 ? "" : event.ageRating(),
                                            event.contentTypes().isEmpty() ? "" : event.contentTypes().get(0)));
                    String title = event.name();
                    if (title.length() > 40) title = title.substring(0, 37) + "...";
                    String id = "0x" + Integer.toHexString(event.id());
                    Element programCard = cardContainer.appendElement("div");
                    programCard.addClass("program-card");
                    programCard.attr("start-time", Long.toString(event.startTime()));
                    programCard.attr("end-time", Long.toString(event.endTime()));
                    programCard.attr("event", entry.getKey() + "/" + id);
                    Element titleSpan = programCard.appendElement("span");
                    titleSpan.addClass("card-title");
                    titleSpan.html(title);
                    programCard.appendElement("br");
                    Element dateSpan = programCard.appendElement("span");
                    dateSpan.addClass("card-time");
                    dateSpan.html(dateFmt.format(new Date(event.startTime())));
                    programCard.append(" ");
                    Element timeSpan = programCard.appendElement("span");
                    timeSpan.addClass("card-time");
                    timeSpan
                            .html(TIME_FMT.format(new Date(event.startTime())) + " - "
                                    + TIME_FMT.format(new Date(event.endTime())));

                    String details = String
                            .format("""
                                    <strong>%s: </strong>%s<br/>
                                    <strong>%s: </strong>%s<br/>
                                    <strong>%s: </strong>%s<br/>
                                    <strong>%s: </strong>%s (to %s)<br/>
                                    <strong>%s: </strong>%s<br/>
                                    <strong>%s: </strong>%s<br/>
                                    <br/>
                                    %s
                                    """, locale.getProperty("title", "Title"), event.name(),
                                    locale.getProperty("channel", "Channel"), entry.getKey(),
                                    locale.getProperty("e_date", "Emission date"),
                                    dateFmtExt.format(new Date(event.startTime())) + " "
                                            + locale.getProperty("at", "at") + " "
                                            + TIME_FMT.format(new Date(event.startTime())),
                                    locale.getProperty("duration", "Duration"),
                                    Duration.ofMillis(event.endTime() - event.startTime()).toString().substring(2),
                                    TIME_FMT.format(new Date(event.endTime())), locale.getProperty("genre", "Genre"),
                                    String.join(", ", event.contentTypes().toArray(new String[0])),
                                    locale.getProperty("age", "Age rating"),
                                    event.ageRating() == -1 ? locale.getProperty("none", "None")
                                            : event.ageRating() + "+",
                                    event.description());
                    File eventFile = new File(outputDir, "events/" + entry.getKey() + "/" + id + ".html");
                    eventFile.getParentFile().mkdirs();
                    try (Writer wr = new FileWriter(eventFile)) {
                        wr.write(String.format(detailsHTML.toString(), event.name(), details));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.err.println("Writing HTML file...");
        try (Writer writer = new FileWriter(new File(outputDir, "index.html"), StandardCharsets.UTF_8)) {
            writer.write(doc.toString());
        }

        System.err.println("All done!");
    }
}
