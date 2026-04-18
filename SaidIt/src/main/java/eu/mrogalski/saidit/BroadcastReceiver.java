package eu.mrogalski.saidit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

public class BroadcastReceiver extends android.content.BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("skip_tutorial", false)) {
            return;
        }
        if (prefs.getBoolean(SaidIt.SCHEDULE_ENABLED_KEY, false) && !isWithinSchedule(prefs)) {
            return;
        }
        context.startForegroundService(new Intent(context, SaidItService.class));
    }

    private static boolean isWithinSchedule(SharedPreferences prefs) {
        int startHour = prefs.getInt(SaidIt.SCHEDULE_START_HOUR_KEY, 8);
        int startMinute = prefs.getInt(SaidIt.SCHEDULE_START_MINUTE_KEY, 0);
        int endHour = prefs.getInt(SaidIt.SCHEDULE_END_HOUR_KEY, 23);
        int endMinute = prefs.getInt(SaidIt.SCHEDULE_END_MINUTE_KEY, 0);

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
