package com.example.social.client.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** «щойно», «5 хв тому», «3 год тому», «вчора», «12 листопада». */
public final class TimeAgo {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.of("uk"));

    private TimeAgo() {
    }

    public static String format(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        Duration age = Duration.between(time, LocalDateTime.now());
        long minutes = age.toMinutes();
        if (minutes < 1) {
            return "щойно";
        }
        if (minutes < 60) {
            return minutes + " хв тому";
        }
        long hours = age.toHours();
        if (hours < 24) {
            return hours + " год тому";
        }
        if (hours < 48) {
            return "вчора";
        }
        return time.format(DATE);
    }
}