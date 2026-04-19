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

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.content.SharedPreferences;

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
    private TextView history_limit;
    private TextView history_size;
    private com.google.android.material.progressindicator.LinearProgressIndicator memoryProgress;
    private com.google.android.material.chip.ChipGroup durationChips;
    private com.google.android.material.button.MaterialButton saveButton;
    private float selectedSeconds = 120;

    private LinearLayout rec_section;
    private TextView rec_indicator;
    private TextView rec_time;
    private Button record_pause_button;

    private View statusDot;
    private TextView statusLabel;
    private WaveformView waveform;
    private final float[] amplitudeSnapshot = new float[32];

    private LinearLayout lastSavedSection;
    private TextView lastSavedTime;
    private com.google.android.material.button.MaterialButton lastSavedPlay;
    private String lastSavedFilePath;


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

        durationChips = rootView.findViewById(R.id.duration_chips);
        saveButton = rootView.findViewById(R.id.save_button);

        durationChips.check(R.id.chip_2m);
        durationChips.setOnCheckedStateChangeListener(new com.google.android.material.chip.ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull com.google.android.material.chip.ChipGroup group, @NonNull java.util.List<Integer> checkedIds) {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                selectedSeconds = getSecondsForChip(id);
                updateSaveButtonText();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClickListener.recordSeconds(selectedSeconds, false);
            }
        });
        saveButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                recordButtonClickListener.recordSeconds(selectedSeconds, true);
                return true;
            }
        });

        ready_section = rootView.findViewById(R.id.ready_section);
        rec_section = rootView.findViewById(R.id.rec_section);
        rec_indicator = rootView.findViewById(R.id.rec_indicator);
        rec_time = rootView.findViewById(R.id.rec_time);

        lastSavedSection = rootView.findViewById(R.id.last_saved_section);
        lastSavedTime = rootView.findViewById(R.id.last_saved_time);
        lastSavedPlay = rootView.findViewById(R.id.last_saved_play);

        SharedPreferences prefs = activity.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        lastSavedFilePath = prefs.getString(SaidIt.LAST_SAVED_FILE_KEY, null);
        if (lastSavedFilePath != null && new File(lastSavedFilePath).exists()) {
            lastSavedSection.setVisibility(View.VISIBLE);
            updateLastSavedTime(prefs);
        }

        lastSavedPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastSavedFilePath == null) return;
                File file = new File(lastSavedFilePath);
                if (!file.exists()) return;
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri fileUri = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".provider", file);
                intent.setDataAndType(fileUri, "audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

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
                        listenToggleButton.setText(R.string.stop);
                        listenToggleButton.setIconResource(R.drawable.ic_stop);
                        waveform.setBarColor(MaterialColors.getColor(waveform, com.google.android.material.R.attr.colorPrimary));
                        waveform.setActive(true);
                        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
                        dot.setColor(MaterialColors.getColor(statusDot, com.google.android.material.R.attr.colorPrimary));
                    } else {
                        statusLabel.setText(R.string.listening_disabled_enable);
                        listenToggleButton.setText(R.string.listen_start);
                        listenToggleButton.setIconResource(R.drawable.ic_play_arrow);
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

            if (lastSavedFilePath != null && activity != null) {
                updateLastSavedTime(activity.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE));
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

    private class RecordButtonClickListener implements View.OnClickListener {

        @Override
        public void onClick(final View v) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, final boolean recording, float memorized, float totalMemory, float recorded) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (recording) {
                                echo.stopRecording(new PromptFileReceiver(getActivity()), "");
                            }
                        }
                    });
                }
            });
        }

        public void recordSeconds(final float seconds, final boolean keepRecording) {
            echo.getState(new SaidItService.StateCallback() {
                @Override
                public void state(final boolean listeningEnabled, final boolean recording, float memorized, float totalMemory, float recorded) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (recording) {
                                echo.stopRecording(new PromptFileReceiver(getActivity()), "");
                                return;
                            }
                            if (keepRecording) {
                                @SuppressLint("ValidFragment")
                                final WorkingDialog pd = new WorkingDialog();
                                pd.setDescriptionStringId(R.string.work_default);
                                pd.show(getParentFragmentManager(), "Recording");
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
                            }
                        }
                    });
                }
            });
        }
    }

    private float getSecondsForChip(int chipId) {
        if (chipId == R.id.chip_30s) return 30;
        if (chipId == R.id.chip_2m) return 120;
        if (chipId == R.id.chip_5m) return 300;
        if (chipId == R.id.chip_30m) return 1800;
        if (chipId == R.id.chip_max) return 60 * 60 * 24 * 365;
        return 120;
    }

    private String getLabelForChip(int chipId) {
        if (chipId == R.id.chip_30s) return "30s";
        if (chipId == R.id.chip_2m) return "2 min";
        if (chipId == R.id.chip_5m) return "5 min";
        if (chipId == R.id.chip_30m) return "30 min";
        if (chipId == R.id.chip_max) return "all";
        return "2 min";
    }

    private void updateSaveButtonText() {
        int checkedId = durationChips.getCheckedChipId();
        String label = getLabelForChip(checkedId);
        saveButton.setText(getString(R.string.save_last_format, label));
    }

    private void updateLastSavedTime(SharedPreferences prefs) {
        long savedTime = prefs.getLong(SaidIt.LAST_SAVED_TIME_KEY, 0);
        if (savedTime == 0) return;
        long elapsed = System.currentTimeMillis() - savedTime;
        if (elapsed < 60_000) {
            lastSavedTime.setText(R.string.last_saved_just_now);
        } else {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(savedTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            lastSavedTime.setText(relative);
        }
    }

    private void onFileSaved(final File file) {
        final Activity activity = getActivity();
        if (activity == null) return;
        SharedPreferences prefs = activity.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(SaidIt.LAST_SAVED_FILE_KEY, file.getAbsolutePath())
            .putLong(SaidIt.LAST_SAVED_TIME_KEY, System.currentTimeMillis())
            .apply();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lastSavedFilePath = file.getAbsolutePath();
                lastSavedSection.setVisibility(View.VISIBLE);
                updateLastSavedTime(activity.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE));
            }
        });
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

    class PromptFileReceiver implements SaidItService.WavFileReceiver {

        private Activity activity;

        public PromptFileReceiver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void fileReady(final File file, float runtime) {
            onFileSaved(file);
            new RecordingDoneDialog()
                    .setFile(file)
                    .setRuntime(runtime)
                    .show(((androidx.appcompat.app.AppCompatActivity) activity).getSupportFragmentManager(), "Recording Done");
        }
    }
}
