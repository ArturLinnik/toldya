package eu.mrogalski.saidit;

import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;

public class SettingsActivity extends AppCompatActivity {
    static final String TAG = SettingsActivity.class.getSimpleName();
    private final MemoryOnClickListener memoryClickListener = new MemoryOnClickListener();
    private final QualityOnClickListener qualityClickListener = new QualityOnClickListener();

    final WorkingDialog dialog = new WorkingDialog();

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

    private void setButtonHighlight(View view, boolean selected) {
        if (view instanceof Button) {
            Button button = (Button) view;
            if (selected) {
                button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.md_primary)));
                button.setTextColor(getColor(R.color.md_on_primary));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.md_surface_container)));
                button.setTextColor(getColor(R.color.md_on_surface));
            }
        }
    }

    private void syncScheduleUI() {
        final SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        boolean scheduleEnabled = prefs.getBoolean(SaidIt.SCHEDULE_ENABLED_KEY, false);
        int startHour = prefs.getInt(SaidIt.SCHEDULE_START_HOUR_KEY, 8);
        int startMinute = prefs.getInt(SaidIt.SCHEDULE_START_MINUTE_KEY, 0);
        int endHour = prefs.getInt(SaidIt.SCHEDULE_END_HOUR_KEY, 23);
        int endMinute = prefs.getInt(SaidIt.SCHEDULE_END_MINUTE_KEY, 0);

        MaterialSwitch toggle = findViewById(R.id.schedule_toggle);
        Button startTime = findViewById(R.id.schedule_start_time);
        Button endTime = findViewById(R.id.schedule_end_time);
        View timesLayout = findViewById(R.id.schedule_times_layout);

        toggle.setChecked(scheduleEnabled);

        startTime.setText(String.format("%02d:%02d", startHour, startMinute));
        endTime.setText(String.format("%02d:%02d", endHour, endMinute));

        startTime.setEnabled(scheduleEnabled);
        endTime.setEnabled(scheduleEnabled);
        timesLayout.setAlpha(scheduleEnabled ? 1.0f : 0.4f);
    }

    private void syncUI() {
        final long maxMemory = Runtime.getRuntime().maxMemory();

        ((Button) findViewById(R.id.memory_low)).setText(StringFormat.shortFileSize(maxMemory / 4));
        ((Button) findViewById(R.id.memory_medium)).setText(StringFormat.shortFileSize(maxMemory / 2));
        ((Button) findViewById(R.id.memory_high)).setText(StringFormat.shortFileSize((long) (maxMemory * 0.90)));

        TimeFormat.naturalLanguage(getResources(), service.getBytesToSeconds() * service.getMemorySize(), timeFormatResult);
        ((TextView) findViewById(R.id.history_limit)).setText(timeFormatResult.text);

        highlightButtons();
        syncFormatChips();
        syncScheduleUI();
    }

    void highlightButtons() {
        final long maxMemory = Runtime.getRuntime().maxMemory();

        int button = (int)(service.getMemorySize() / (maxMemory / 4));
        highlightButton(R.id.memory_low, R.id.memory_medium, R.id.memory_high, button);

        int samplingRate = service.getSamplingRate();
        if(samplingRate >= 44100) button = 3;
        else if(samplingRate >= 16000) button = 2;
        else button = 1;
        highlightButton(R.id.quality_8kHz, R.id.quality_16kHz, R.id.quality_48kHz, button);
    }

    private void syncFormatChips() {
        SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
        OutputFormat format = OutputFormat.fromPreference(prefs.getString(SaidIt.OUTPUT_FORMAT_KEY, "WAV"));
        ((Chip) findViewById(R.id.format_wav)).setChecked(format == OutputFormat.WAV);
        ((Chip) findViewById(R.id.format_flac)).setChecked(format == OutputFormat.FLAC);
        ((Chip) findViewById(R.id.format_ogg)).setChecked(format == OutputFormat.OGG);
        ((Chip) findViewById(R.id.format_opus)).setChecked(format == OutputFormat.OPUS);
    }

    private void highlightButton(int button1, int button2, int button3, int i) {
        setButtonHighlight(findViewById(button1), 1 == i);
        setButtonHighlight(findViewById(button2), 2 == i);
        setButtonHighlight(findViewById(button3), 3 == i);
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

        findViewById(R.id.memory_low).setOnClickListener(memoryClickListener);
        findViewById(R.id.memory_medium).setOnClickListener(memoryClickListener);
        findViewById(R.id.memory_high).setOnClickListener(memoryClickListener);

        initSampleRateButton(R.id.quality_8kHz, 8000, 11025);
        initSampleRateButton(R.id.quality_16kHz, 16000, 22050);
        initSampleRateButton(R.id.quality_48kHz, 48000, 44100);

        View.OnClickListener formatClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String format;
                int id = v.getId();
                if (id == R.id.format_flac) format = "FLAC";
                else if (id == R.id.format_ogg) format = "OGG";
                else if (id == R.id.format_opus) format = "OPUS";
                else format = "WAV";
                getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE)
                        .edit().putString(SaidIt.OUTPUT_FORMAT_KEY, format).apply();
                syncFormatChips();
            }
        };
        findViewById(R.id.format_wav).setOnClickListener(formatClickListener);
        findViewById(R.id.format_flac).setOnClickListener(formatClickListener);
        findViewById(R.id.format_ogg).setOnClickListener(formatClickListener);
        findViewById(R.id.format_opus).setOnClickListener(formatClickListener);

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

        findViewById(R.id.schedule_start_time).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
                int hour = prefs.getInt(SaidIt.SCHEDULE_START_HOUR_KEY, 8);
                int minute = prefs.getInt(SaidIt.SCHEDULE_START_MINUTE_KEY, 0);
                new TimePickerDialog(SettingsActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minuteOfHour) {
                        getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE).edit()
                                .putInt(SaidIt.SCHEDULE_START_HOUR_KEY, hourOfDay)
                                .putInt(SaidIt.SCHEDULE_START_MINUTE_KEY, minuteOfHour)
                                .apply();
                        syncScheduleUI();
                        if (service != null) {
                            service.applySchedule();
                        }
                    }
                }, hour, minute, true).show();
            }
        });

        findViewById(R.id.schedule_end_time).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE);
                int hour = prefs.getInt(SaidIt.SCHEDULE_END_HOUR_KEY, 23);
                int minute = prefs.getInt(SaidIt.SCHEDULE_END_MINUTE_KEY, 0);
                new TimePickerDialog(SettingsActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minuteOfHour) {
                        getSharedPreferences(SaidIt.PACKAGE_NAME, MODE_PRIVATE).edit()
                                .putInt(SaidIt.SCHEDULE_END_HOUR_KEY, hourOfDay)
                                .putInt(SaidIt.SCHEDULE_END_MINUTE_KEY, minuteOfHour)
                                .apply();
                        syncScheduleUI();
                        if (service != null) {
                            service.applySchedule();
                        }
                    }
                }, hour, minute, true).show();
            }
        });

        dialog.setDescriptionStringId(R.string.work_preparing_memory);
    }

    private void debugPrintCodecs() {
        final int codecCount = MediaCodecList.getCodecCount();
        for(int i = 0; i < codecCount; ++i) {
            final MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if(!info.isEncoder()) continue;
            boolean audioFound = false;
            String types = "";
            final String[] supportedTypes = info.getSupportedTypes();
            for(int j = 0; j < supportedTypes.length; ++j) {
                if(j > 0)
                    types += ", ";
                types += supportedTypes[j];
                if(supportedTypes[j].startsWith("audio")) audioFound = true;
            }
            if(!audioFound) continue;
            Log.d(TAG, "Codec " + i + ": " + info.getName() + " (" + types + ") encoder: " + info.isEncoder());
        }
    }

    private void initSampleRateButton(int buttonId, int primarySampleRate, int secondarySampleRate) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(qualityClickListener);
        if(testSampleRateValid(primarySampleRate)) {
            button.setText(String.format("%d kHz", primarySampleRate / 1000));
            button.setTag(primarySampleRate);
        } else if(testSampleRateValid(secondarySampleRate)) {
            button.setText(String.format("%d kHz", secondarySampleRate / 1000));
            button.setTag(secondarySampleRate);
        } else {
            button.setVisibility(View.GONE);
        }
    }

    private boolean testSampleRateValid(int sampleRate) {
        final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        return bufferSize > 0;
    }

    private class MemoryOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final long memory = getMultiplier(v) * Runtime.getRuntime().maxMemory() / 4;
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

        private int getMultiplier(View button) {
            int id = button.getId();
            if (id == R.id.memory_high) return 3;
            if (id == R.id.memory_medium) return 2;
            if (id == R.id.memory_low) return 1;
            return 0;
        }
    }

    private class QualityOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final int sampleRate = getSampleRate(v);
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

        private int getSampleRate(View button) {
            Object tag = button.getTag();
            if(tag instanceof Integer) {
                return ((Integer) tag).intValue();
            }
            return 8000;
        }
    }
}
