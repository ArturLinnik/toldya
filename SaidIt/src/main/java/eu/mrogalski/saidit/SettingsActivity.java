package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;

public class SettingsActivity extends AppCompatActivity {
    static final String TAG = SettingsActivity.class.getSimpleName();

    final WorkingDialog dialog = new WorkingDialog();
    private boolean syncing = false;

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SaidItService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    SaidItService service;
    ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            syncUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            service = null;
        }
    };

    final TimeFormat.Result timeFormatResult = new TimeFormat.Result();

    private void syncScheduleUI() {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        boolean scheduleEnabled = prefs.getBoolean(SaidIt.SCHEDULE_ENABLED_KEY, false);
        int startHour = prefs.getInt(SaidIt.SCHEDULE_START_HOUR_KEY, 8);
        int startMinute = prefs.getInt(SaidIt.SCHEDULE_START_MINUTE_KEY, 0);
        int endHour = prefs.getInt(SaidIt.SCHEDULE_END_HOUR_KEY, 23);
        int endMinute = prefs.getInt(SaidIt.SCHEDULE_END_MINUTE_KEY, 0);

        MaterialSwitch toggle = findViewById(R.id.schedule_toggle);
        TextView startTime = findViewById(R.id.schedule_start_time);
        TextView endTime = findViewById(R.id.schedule_end_time);
        View timesLayout = findViewById(R.id.schedule_times_layout);

        toggle.setChecked(scheduleEnabled);

        startTime.setText(String.format("%02d:%02d", startHour, startMinute));
        endTime.setText(String.format("%02d:%02d", endHour, endMinute));

        timesLayout.setAlpha(scheduleEnabled ? 1.0f : 0.4f);
        findViewById(R.id.schedule_start_card).setClickable(scheduleEnabled);
        findViewById(R.id.schedule_end_card).setClickable(scheduleEnabled);
    }

    private void syncUI() {
        syncing = true;
        final long maxMemory = Runtime.getRuntime().maxMemory();

        ((Chip) findViewById(R.id.memory_low)).setText(StringFormat.shortFileSize(maxMemory / 4));
        ((Chip) findViewById(R.id.memory_medium)).setText(StringFormat.shortFileSize(maxMemory / 2));
        ((Chip) findViewById(R.id.memory_high)).setText(StringFormat.shortFileSize((long) (maxMemory * 0.90)));

        TimeFormat.naturalLanguage(getResources(), service.getBytesToSeconds() * service.getMemorySize(), timeFormatResult);
        ((TextView) findViewById(R.id.history_limit)).setText(timeFormatResult.text);

        syncMemoryChips();
        syncQualityChips();
        syncFormatChips();
        syncScheduleUI();
        syncing = false;
    }

    private void syncMemoryChips() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        int level = (int) (service.getMemorySize() / (maxMemory / 4));
        ((Chip) findViewById(R.id.memory_low)).setChecked(level == 1);
        ((Chip) findViewById(R.id.memory_medium)).setChecked(level == 2);
        ((Chip) findViewById(R.id.memory_high)).setChecked(level == 3);
    }

    private void syncQualityChips() {
        int samplingRate = service.getSamplingRate();
        int level;
        if (samplingRate >= 44100) level = 3;
        else if (samplingRate >= 16000) level = 2;
        else level = 1;
        ((Chip) findViewById(R.id.quality_8kHz)).setChecked(level == 1);
        ((Chip) findViewById(R.id.quality_16kHz)).setChecked(level == 2);
        ((Chip) findViewById(R.id.quality_48kHz)).setChecked(level == 3);
    }

    private void syncFormatChips() {
        SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        OutputFormat format = OutputFormat.fromPreference(prefs.getString(SaidIt.OUTPUT_FORMAT_KEY, "WAV"));
        ((Chip) findViewById(R.id.format_wav)).setChecked(format == OutputFormat.WAV);
        ((Chip) findViewById(R.id.format_flac)).setChecked(format == OutputFormat.FLAC);
        ((Chip) findViewById(R.id.format_ogg)).setChecked(format == OutputFormat.OGG);
        ((Chip) findViewById(R.id.format_opus)).setChecked(format == OutputFormat.OPUS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ChipGroup memoryChips = findViewById(R.id.memory_chips);
        memoryChips.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull ChipGroup group, @NonNull java.util.List<Integer> checkedIds) {
                if (syncing || checkedIds.isEmpty() || service == null) return;
                int id = checkedIds.get(0);
                int multiplier = getMemoryMultiplier(id);
                if (multiplier == 0) return;
                final long memory = multiplier * Runtime.getRuntime().maxMemory() / 4;
                dialog.show(getSupportFragmentManager(), "Preparing memory");
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        service.setMemorySize(memory);
                        service.getState(new SaidItService.StateCallback() {
                            @Override
                            public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
                                syncUI();
                                if (dialog.isVisible()) dialog.dismiss();
                            }
                        });
                    }
                });
            }
        });

        initSampleRateChip(R.id.quality_8kHz, 8000, 11025);
        initSampleRateChip(R.id.quality_16kHz, 16000, 22050);
        initSampleRateChip(R.id.quality_48kHz, 48000, 44100);

        ChipGroup qualityChips = findViewById(R.id.quality_chips);
        qualityChips.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull ChipGroup group, @NonNull java.util.List<Integer> checkedIds) {
                if (syncing || checkedIds.isEmpty() || service == null) return;
                Chip chip = findViewById(checkedIds.get(0));
                Object tag = chip.getTag();
                if (!(tag instanceof Integer)) return;
                final int sampleRate = (Integer) tag;
                dialog.show(getSupportFragmentManager(), "Preparing memory");
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        service.setSampleRate(sampleRate);
                        service.getState(new SaidItService.StateCallback() {
                            @Override
                            public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded) {
                                syncUI();
                                if (dialog.isVisible()) dialog.dismiss();
                            }
                        });
                    }
                });
            }
        });

        ChipGroup formatChips = findViewById(R.id.format_chips);
        formatChips.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull ChipGroup group, @NonNull java.util.List<Integer> checkedIds) {
                if (syncing || checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                String format;
                if (id == R.id.format_flac) format = "FLAC";
                else if (id == R.id.format_ogg) format = "OGG";
                else if (id == R.id.format_opus) format = "OPUS";
                else format = "WAV";
                getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE)
                        .edit().putString(SaidIt.OUTPUT_FORMAT_KEY, format).apply();
                syncFormatChips();
            }
        });

        ((MaterialSwitch) findViewById(R.id.schedule_toggle)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE)
                        .edit().putBoolean(SaidIt.SCHEDULE_ENABLED_KEY, isChecked).apply();
                syncScheduleUI();
                if (service != null) {
                    service.applySchedule();
                }
            }
        });

        findViewById(R.id.schedule_start_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
                int hour = prefs.getInt(SaidIt.SCHEDULE_START_HOUR_KEY, 8);
                int minute = prefs.getInt(SaidIt.SCHEDULE_START_MINUTE_KEY, 0);
                MaterialTimePicker picker = new MaterialTimePicker.Builder()
                        .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                        .setHour(hour)
                        .setMinute(minute)
                        .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                        .build();
                picker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE).edit()
                                .putInt(SaidIt.SCHEDULE_START_HOUR_KEY, picker.getHour())
                                .putInt(SaidIt.SCHEDULE_START_MINUTE_KEY, picker.getMinute())
                                .apply();
                        syncScheduleUI();
                        if (service != null) {
                            service.applySchedule();
                        }
                    }
                });
                picker.show(getSupportFragmentManager(), "start_time");
            }
        });

        findViewById(R.id.schedule_end_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
                int hour = prefs.getInt(SaidIt.SCHEDULE_END_HOUR_KEY, 23);
                int minute = prefs.getInt(SaidIt.SCHEDULE_END_MINUTE_KEY, 0);
                MaterialTimePicker picker = new MaterialTimePicker.Builder()
                        .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                        .setHour(hour)
                        .setMinute(minute)
                        .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                        .build();
                picker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE).edit()
                                .putInt(SaidIt.SCHEDULE_END_HOUR_KEY, picker.getHour())
                                .putInt(SaidIt.SCHEDULE_END_MINUTE_KEY, picker.getMinute())
                                .apply();
                        syncScheduleUI();
                        if (service != null) {
                            service.applySchedule();
                        }
                    }
                });
                picker.show(getSupportFragmentManager(), "end_time");
            }
        });

        dialog.setDescriptionStringId(R.string.work_preparing_memory);
    }

    private int getMemoryMultiplier(int chipId) {
        if (chipId == R.id.memory_high) return 3;
        if (chipId == R.id.memory_medium) return 2;
        if (chipId == R.id.memory_low) return 1;
        return 0;
    }

    private void initSampleRateChip(int chipId, int primarySampleRate, int secondarySampleRate) {
        Chip chip = findViewById(chipId);
        if (testSampleRateValid(primarySampleRate)) {
            chip.setText(String.format("%d kHz", primarySampleRate / 1000));
            chip.setTag(primarySampleRate);
        } else if (testSampleRateValid(secondarySampleRate)) {
            chip.setText(String.format("%d kHz", secondarySampleRate / 1000));
            chip.setTag(secondarySampleRate);
        } else {
            chip.setVisibility(View.GONE);
        }
    }

    private boolean testSampleRateValid(int sampleRate) {
        final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        return bufferSize > 0;
    }
}
