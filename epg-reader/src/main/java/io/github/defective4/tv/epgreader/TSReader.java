package io.github.defective4.tv.epgreader;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import nl.digitalekabeltelevisie.data.mpeg.PSI;
import nl.digitalekabeltelevisie.data.mpeg.TransportStream;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ContentDescriptor;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ContentDescriptor.ContentItem;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.Descriptor;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ExtendedEventDescriptor;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ParentalRatingDescriptor;
import nl.digitalekabeltelevisie.data.mpeg.descriptors.ParentalRatingDescriptor.Rating;
import nl.digitalekabeltelevisie.data.mpeg.psi.EIT;
import nl.digitalekabeltelevisie.data.mpeg.psi.EITsection;
import nl.digitalekabeltelevisie.data.mpeg.psi.EITsection.Event;
import nl.digitalekabeltelevisie.data.mpeg.psi.PAT;
import nl.digitalekabeltelevisie.data.mpeg.psi.PATsection;
import nl.digitalekabeltelevisie.data.mpeg.psi.PATsection.Program;
import nl.digitalekabeltelevisie.util.ServiceIdentification;
import nl.digitalekabeltelevisie.util.Utils;

public class TSReader {

    private static final DateFormat FMT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    static {
        FMT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static Map<String, List<FriendlyEvent>> readTransportStream(File file) throws Exception {
        TransportStream ts = new TransportStream(file);
        ts.parseStream(null);
        PSI psi = ts.getPsi();
        PAT allocationTable = psi.getPat();
        EIT eit = psi.getEit();
        Map<Integer, String> programs = new LinkedHashMap<>();
        for (PATsection sect : allocationTable.getPATsections())
            if (sect != null) for (Program prog : sect.getPrograms())
                if (prog != null) programs.put(prog.getProgram_number(), prog.getServiceNameOrNit());

        Map<String, List<FriendlyEvent>> events = new LinkedHashMap<>();
        Map<ServiceIdentification, EITsection[]> schedule = eit.getCombinedSchedule();
        for (Map.Entry<ServiceIdentification, EITsection[]> entry : schedule.entrySet()) {
            String serviceName = programs.getOrDefault(entry.getKey().serviceId(), "Unknown Channel");
            for (EITsection sect : entry.getValue()) {
                if (sect == null) continue;
                for (Event ev : sect.getEventList()) {
                    String name = ev.getEventName();
                    String durationStr = ev.getDuration();
                    long duration = Utils.getDurationSeconds(durationStr);
                    int id = ev.getEventID();
                    Date startTime = FMT.parse(Utils.getEITStartTimeAsString(ev.getStartTime()));
                    StringBuilder description = new StringBuilder();
                    List<String> contentTypes = new ArrayList<>();
                    int minimumAge = -1;

                    for (Descriptor desc : ev.getDescriptorList()) {
                        if (desc instanceof ExtendedEventDescriptor ex) {
                            description.append(ex.getText().toString());
                        } else if (desc instanceof ContentDescriptor cd) {
                            for (ContentItem item : cd.getContentList()) {
                                int l1 = item.contentNibbleLevel1();
                                int l2 = item.contentNibbleLevel2();
                                contentTypes.add(ContentDescriptor.getContentNibbleLevel2String(l1, l2));
                            }
                        } else if (desc instanceof ParentalRatingDescriptor pr) {
                            for (Rating r : pr.getRatingList()) {
                                int type = r.getRating();
                                if (1 <= type && type <= 15) {
                                    {
                                        minimumAge = type + 3;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    if (!events.containsKey(serviceName)) events.put(serviceName, new ArrayList<>());
                    events
                            .get(serviceName)
                            .add(new FriendlyEvent(id, name, startTime.getTime(), startTime.getTime() + duration * 1000,
                                    description.toString(), Collections.unmodifiableList(contentTypes), minimumAge));
                }
            }
        }
        return Collections.unmodifiableMap(events);
    }
}
