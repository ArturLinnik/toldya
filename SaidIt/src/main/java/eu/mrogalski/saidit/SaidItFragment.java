package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import android.text.format.DateUtils;

import eu.mrogalski.android.TimeFormat;

public class SaidItFragment extends Fragment {

    private static final String TAG = SaidItFragment.class.getSimpleName();
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private com.google.android.material.button.MaterialButton listenToggleButton;

    ListenButtonClickListener listenButtonClickListener = new ListenButtonClickListener();
    RecordButtonClickListener recordButtonClickListener = new RecordButtonClickListener();

    private boolean isListening = false;
    private boolean isRecording = false;

    private LinearLayout ready_section;
    private Button recordLastFiveMinutesButton;
    private Button recordMaxButton;
    private Button recordLastMinuteButton;
    private Button recordLastThirtyMinuteButton;
    private Button recordLastTwoHrsButton;
    private Button recordLastSixHrsButton;
    private TextView history_limit;
    private TextView history_size;
    private com.google.android.material.progressindicator.LinearProgressIndicator memoryProgress;

    private LinearLayout rec_section;
    private TextView rec_indicator;
    private TextView rec_time;
    private Button record_pause_button;

    private MaterialCardView statusCard;
    private View statusDot;
    private TextView statusLabel;
    private WaveformView waveform;
    private final float[] amplitudeSnapshot = new float[32];


    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        final Activity activity = getActivity();
        assert activity != null;
        activity.bindService(new Intent(activity, SaidItService.class), echoConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        final Activity activity = getActivity();
        assert activity != null;
        activity.unbindService(echoConnection);
        echo = null;
    }

    class ActivityResult {
        final int requestCode;
        final int resultCode;
        final Intent data;

        ActivityResult(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    private Runnable updater = new Runnable() {
        @Override
        public void run() {
            final View view = getView();
            if (view == null) return;
            if (echo == null) return;
            echo.getState(serviceStateCallback);
        }
    };

    SaidItService echo;
    private ServiceConnection echoConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            if (echo != null && echo == typedBinder.getService()) {
                Log.d(TAG, "update loop already running, skipping");
                return;
            }
            echo = typedBinder.getService();
            getView().postOnAnimation(updater);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisconnected");
            echo = null;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_background_recorder, container, false);
        if (rootView == null) return null;

        final Activity activity = getActivity();

        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_settings) {
                    startActivity(new Intent(activity, SettingsActivity.class));
                    return true;
                }
                return false;
            }
        });

        statusCard = rootView.findViewById(R.id.status_card);
        statusDot = rootView.findViewById(R.id.status_dot);
        statusLabel = rootView.findViewById(R.id.status_label);
        waveform = rootView.findViewById(R.id.waveform);

        history_limit = rootView.findViewById(R.id.history_limit);
        history_size = rootView.findViewById(R.id.history_size);
        memoryProgress = rootView.findViewById(R.id.memory_progress);

        listenToggleButton = rootView.findViewById(R.id.listen_toggle_button);
        listenToggleButton.setOnClickListener(listenButtonClickListener);

        record_pause_button = rootView.findViewById(R.id.rec_stop_button);
        record_pause_button.setOnClickListener(recordButtonClickListener);

        recordLastMinuteButton = rootView.findViewById(R.id.record_last_minute);
        recordLastMinuteButton.setOnClickListener(recordButtonClickListener);
        recordLastMinuteButton.setOnLongClickListener(recordButtonClickListener);

        recordLastFiveMinutesButton = rootView.findViewById(R.id.record_last_5_minutes);
        recordLastFiveMinutesButton.setOnClickListener(recordButtonClickListener);
        recordLastFiveMinutesButton.setOnLongClickListener(recordButtonClickListener);

        recordLastThirtyMinuteButton = rootView.findViewById(R.id.record_last_30_minutes);
        recordLastThirtyMinuteButton.setOnClickListener(recordButtonClickListener);
        recordLastThirtyMinuteButton.setOnLongClickListener(recordButtonClickListener);

        recordLastTwoHrsButton = rootView.findViewById(R.id.record_last_2_hrs);
        recordLastTwoHrsButton.setOnClickListener(recordButtonClickListener);
        recordLastTwoHrsButton.setOnLongClickListener(recordButtonClickListener);

        recordLastSixHrsButton = rootView.findViewById(R.id.record_last_6_hrs);
        recordLastSixHrsButton.setOnClickListener(recordButtonClickListener);
        recordLastSixHrsButton.setOnLongClickListener(recordButtonClickListener);

        recordMaxButton = rootView.findViewById(R.id.record_last_max);
        recordMaxButton.setOnClickListener(recordButtonClickListener);
        recordMaxButton.setOnLongClickListener(recordButtonClickListener);

        ready_section = rootView.findViewById(R.id.ready_section);
        rec_section = rootView.findViewById(R.id.rec_section);
        rec_indicator = rootView.findViewById(R.id.rec_indicator);
        rec_time = rootView.findViewById(R.id.rec_time);

        serviceStateCallback.state(isListening, isRecording, 0, 0, 0);
        return rootView;
    }

    private SaidItService.StateCallback serviceStateCallback = new SaidItService.StateCallback() {
        @Override
        public void state(final boolean listeningEnabled, final boolean recording, final float memorized, final float totalMemory, final float recorded) {
            final Activity activity = getActivity();
            if (activity == null) return;
            final Resources resources = activity.getResources();
            if ((isRecording != recording) || (isListening != listeningEnabled)) {
                if (recording != isRecording) {
                    isRecording = recording;
                    rec_section.setVisibility(recording ? View.VISIBLE : View.GONE);
                }

                if (listeningEnabled != isListening) {
                    isListening = listeningEnabled;
                    if (listeningEnabled) {
                        statusLabel.setText(R.string.listening_enabled_disable);
                        listenToggleButton.setIconResource(R.drawable.ic_stop);
                        listenToggleButton.setIconTint(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(listenToggleButton, com.google.android.material.R.attr.colorOnPrimaryContainer)));
                        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorPrimaryContainer));
                        waveform.setBarColor(MaterialColors.getColor(waveform, com.google.android.material.R.attr.colorPrimary));
                        waveform.setActive(true);
                        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
                        dot.setColor(MaterialColors.getColor(statusDot, com.google.android.material.R.attr.colorPrimary));
                    } else {
                        statusLabel.setText(R.string.listening_disabled_enable);
                        listenToggleButton.setIconResource(R.drawable.ic_play_arrow);
                        listenToggleButton.setIconTint(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(listenToggleButton, com.google.android.material.R.attr.colorOnSurfaceVariant)));
                        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorSurfaceContainerHigh));
                        waveform.setActive(false);
                        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
                        dot.setColor(MaterialColors.getColor(statusDot, com.google.android.material.R.attr.colorOutline));
                    }
                }

                if (listeningEnabled && !recording) {
                    ready_section.setVisibility(View.VISIBLE);
                } else {
                    ready_section.setVisibility(View.GONE);
                }
            }

            String sizeText = TimeFormat.shortTimer(memorized);
            String limitText = TimeFormat.shortTimer(totalMemory);
            if (!sizeText.equals(history_size.getText().toString())) {
                history_size.setText(sizeText);
                recordMaxButton.setText(sizeText);
            }
            if (!limitText.equals(history_limit.getText().toString())) {
                history_limit.setText(limitText);
            }
            int progress = totalMemory > 0 ? (int) (memorized / totalMemory * 100) : 0;
            memoryProgress.setProgress(progress);

            TimeFormat.naturalLanguage(resources, recorded, timeFormatResult);

            if (!rec_time.getText().equals(timeFormatResult.text)) {
                rec_indicator.setText(resources.getQuantityText(R.plurals.recorded, timeFormatResult.count));
                rec_time.setText(timeFormatResult.text);
            }

            if (echo != null && isListening) {
                echo.getAmplitudes(amplitudeSnapshot);
                waveform.setAmplitudes(amplitudeSnapshot);
            }

            history_size.postOnAnimationDelayed(updater, 100);
        }
    };

    final TimeFormat.Result timeFormatResult = new TimeFormat.Result();


    private class ListenButtonClickListener implements View.OnClickListener {

        @SuppressLint("ValidFragment")
        final WorkingDialog dialog = new WorkingDialog();

        public ListenButtonClickListener() {
            dialog.setDescriptionStringId(R.string.work_preparing_memory);
        }

        @Override
        public void onClick(View v) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
                    if (listeningEnabled) {
                        new MaterialAlertDialogBuilder(getActivity())
                            .setTitle(R.string.stop_echo_title)
                            .setMessage(R.string.stop_echo_message)
                            .setPositiveButton(R.string.stop, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    echo.disableListening();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    } else {
                        dialog.show(getParentFragmentManager(), "Preparing memory");

                        new Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                echo.enableListening();
                                echo.getState(new SaidItService.StateCallback() {
                                    @Override
                                    public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
                                        dialog.dismiss();
                                    }
                                });
                            }
                        });
                    }
                }
            });
        }
    }

    private class RecordButtonClickListener implements View.OnClickListener, View.OnLongClickListener {

        @Override
        public void onClick(final View v) {
            record(v, false);
        }

        @Override
        public boolean onLongClick(final View v) {
            record(v, true);
            return true;
        }

        public void record(final View button, final boolean keepRecording) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, final boolean recording, float memorized, float totalMemory, float recorded) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (recording) {
                                echo.stopRecording(new PromptFileReceiver(getActivity()),"");
                            } else {
                                @SuppressLint("ValidFragment")
                                final WorkingDialog pd = new WorkingDialog();
                                pd.setDescriptionStringId(R.string.work_default);
                                pd.show(getParentFragmentManager(), "Recording");
                                final float seconds = getPrependedSeconds(button);
                                if (keepRecording) {
                                    echo.startRecording(seconds);
                                } else {
                                    View dialogView = View.inflate(getActivity(), R.layout.dialog_save_recording, null);
                                    EditText fileName = dialogView.findViewById(R.id.recording_name);
                                    TextView extensionLabel = dialogView.findViewById(R.id.recording_extension);
                                    String formatPref = getActivity().getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE)
                                            .getString(SaidIt.OUTPUT_FORMAT_KEY, "WAV");
                                    OutputFormat outputFormat = OutputFormat.fromPreference(formatPref);
                                    extensionLabel.setText("." + outputFormat.extension);
                                    long startMillis = System.currentTimeMillis() - (long)(seconds * 1000);
                                    int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                                    String defaultName = "Echo - " + DateUtils.formatDateTime(getActivity(), startMillis, flags);
                                    fileName.setText(defaultName);
                                    fileName.selectAll();
                                    new MaterialAlertDialogBuilder(getActivity())
                                        .setView(dialogView)
                                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                echo.dumpRecording(seconds, new PromptFileReceiver(getActivity()), fileName.getText().toString());
                                            }
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                                    pd.dismiss();
                                }
                            }
                        }
                    });
                }
            });
        }

        float getPrependedSeconds(View button) {
            switch (button.getId()) {
                case R.id.record_last_minute:
                    return 60;
                case R.id.record_last_5_minutes:
                    return 60 * 5;
                case R.id.record_last_30_minutes:
                    return 60 * 30;
                case R.id.record_last_2_hrs:
                    return 60 * 60 * 2;
                case R.id.record_last_6_hrs:
                    return 60 * 60 * 6;
                case R.id.record_last_max:
                    return 60 * 60 * 24 * 365;
            }
            return 0;
        }
    }

    static Notification buildNotificationForFile(Context context, File outFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", outFile);
        String mimeType = java.net.URLConnection.guessContentTypeFromName(outFile.getName());
        if (mimeType == null) mimeType = "audio/*";
        intent.setDataAndType(fileUri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.recording_saved))
                .setContentText(outFile.getName())
                .setSmallIcon(R.drawable.ic_stat_notify_recorded)
                .setTicker(outFile.getName())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        return notificationBuilder.build();
    }

    static class NotifyFileReceiver implements SaidItService.WavFileReceiver {

        private Context context;

        public NotifyFileReceiver(Context context) {
            this.context = context;
        }

        @Override
        public void fileReady(final File file, float runtime) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            notificationManager.notify(43, buildNotificationForFile(context, file));
        }
    }

    static class PromptFileReceiver implements SaidItService.WavFileReceiver {

        private Activity activity;

        public PromptFileReceiver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void fileReady(final File file, float runtime) {
            new RecordingDoneDialog()
                    .setFile(file)
                    .setRuntime(runtime)
                    .show(((androidx.appcompat.app.AppCompatActivity) activity).getSupportFragmentManager(), "Recording Done");
        }
    }
}
