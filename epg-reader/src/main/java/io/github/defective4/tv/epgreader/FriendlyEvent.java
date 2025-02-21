package io.github.defective4.tv.epgreader;

import java.util.List;

public record FriendlyEvent(int id, String name, long startTime, long endTime, String description,
        List<String> contentTypes, int ageRating) {
}
