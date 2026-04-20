package eu.mrogalski.saidit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BroadcastReceiver extends android.content.BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("skip_tutorial", false)) {
            return;
        }
        if (!SaidIt.isWithinSchedule(prefs)) {
            return;
        }
        context.startForegroundService(new Intent(context, SaidItService.class));
    }
}
