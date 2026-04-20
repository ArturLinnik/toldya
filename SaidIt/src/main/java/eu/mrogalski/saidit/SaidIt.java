package eu.mrogalski.saidit;

import android.content.SharedPreferences;
import java.util.Calendar;

public class SaidIt {

    static final String PACKAGE_NAME = "eu.mrogalski.saidit";
    static final String AUDIO_MEMORY_ENABLED_KEY = "audio_memory_enabled";
    static final String AUDIO_MEMORY_SIZE_KEY = "audio_memory_size";
    static final String SAMPLE_RATE_KEY = "sample_rate";
    static final String OUTPUT_FORMAT_KEY = "output_format";
    static final String SCHEDULE_ENABLED_KEY = "schedule_enabled";
    static final String SCHEDULE_START_HOUR_KEY = "schedule_start_hour";
    static final String SCHEDULE_START_MINUTE_KEY = "schedule_start_minute";
    static final String SCHEDULE_END_HOUR_KEY = "schedule_end_hour";
    static final String SCHEDULE_END_MINUTE_KEY = "schedule_end_minute";
    static final String STORAGE_DIRECTORY_URI_KEY = "storage_directory_uri";
    static final String LAST_SAVED_FILE_KEY = "last_saved_file";
    static final String LAST_SAVED_TIME_KEY = "last_saved_time";

    static boolean isWithinSchedule(SharedPreferences prefs) {
        if (!prefs.getBoolean(SCHEDULE_ENABLED_KEY, false)) {
            return true;
        }
        int startHour = prefs.getInt(SCHEDULE_START_HOUR_KEY, 8);
        int startMinute = prefs.getInt(SCHEDULE_START_MINUTE_KEY, 0);
        int endHour = prefs.getInt(SCHEDULE_END_HOUR_KEY, 23);
        int endMinute = prefs.getInt(SCHEDULE_END_MINUTE_KEY, 0);

        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;

        if (startMinutes <= endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        } else {
            return nowMinutes >= startMinutes || nowMinutes < endMinutes;
        }
    }

}
