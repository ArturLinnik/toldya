package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import static eu.mrogalski.saidit.SaidIt.*;

public class SaidItService extends Service {
    static final String TAG = SaidItService.class.getSimpleName();
    private static final int FOREGROUND_NOTIFICATION_ID = 458;
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";

    volatile int SAMPLE_RATE;
    volatile int FILL_RATE;

    private static final int AMPLITUDE_BUFFER_SIZE = 32;
    private final float[] amplitudeBuffer = new float[AMPLITUDE_BUFFER_SIZE];
    private int amplitudeIndex = 0;

    File outputFile;
    AudioRecord audioRecord; // used only in the audio thread
    AudioFileWriter audioFileWriter; // used only in the audio thread
    final AudioMemory audioMemory = new AudioMemory(); // used only in the audio thread

    HandlerThread audioThread;
    Handler audioHandler; // used to post messages to audio thread

    @Override
    public void onCreate() {

        Log.d(TAG, "Reading native sample rate");

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SAMPLE_RATE = preferences.getInt(SAMPLE_RATE_KEY, AudioTrack.getNativeOutputSampleRate (AudioManager.STREAM_MUSIC));
        Log.d(TAG, "Sample rate: " + SAMPLE_RATE);
        FILL_RATE = 2 * SAMPLE_RATE;

        audioThread = new HandlerThread("audioThread", Thread.MAX_PRIORITY);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            if (isWithinSchedule()) {
                innerStartListening();
            }
            if (preferences.getBoolean(SCHEDULE_ENABLED_KEY, false)) {
                scheduleNextCheck();
            }
        }

    }

    @Override
    public void onDestroy() {
        cancelScheduleCheck();
        stopRecording(null, "");
        innerStopListening();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BackgroundRecorderBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    public void enableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, true).commit();

        if (isWithinSchedule()) {
            innerStartListening();
        }
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(SCHEDULE_ENABLED_KEY, false)) {
            scheduleNextCheck();
        }
    }

    public void disableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, false).commit();

        innerStopListening();
    }

    int state;

    static final int STATE_READY = 0;
    static final int STATE_LISTENING = 1;
    static final int STATE_RECORDING = 2;

    private void innerStartListening() {
        switch(state) {
            case STATE_READY:
                break;
            case STATE_LISTENING:
            case STATE_RECORDING:
                return;
        }
        state = STATE_LISTENING;

        Log.d(TAG, "Queueing: START LISTENING");

        startService(new Intent(this, this.getClass()));

        final long memorySize = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).getLong(AUDIO_MEMORY_SIZE_KEY, Runtime.getRuntime().maxMemory() / 4);

        audioHandler.post(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                Log.d(TAG, "Executing: START LISTENING");
                Log.d(TAG, "Audio: INITIALIZING AUDIO_RECORD");

                audioRecord = new AudioRecord(
                       MediaRecorder.AudioSource.MIC,
                       SAMPLE_RATE,
                       AudioFormat.CHANNEL_IN_MONO,
                       AudioFormat.ENCODING_PCM_16BIT,
                       AudioMemory.CHUNK_SIZE);

                if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Audio: INITIALIZATION ERROR - releasing resources");
                    audioRecord.release();
                    audioRecord = null;
                    state = STATE_READY;
                    return;
                }

                Log.d(TAG, "Audio: STARTING AudioRecord");
                audioMemory.allocate(memorySize);

                Log.d(TAG, "Audio: STARTING AudioRecord");
                audioRecord.startRecording();
                audioHandler.post(audioReader);
            }
        });


    }

    private void innerStopListening() {
        switch(state) {
            case STATE_READY:
            case STATE_RECORDING:
                return;
            case STATE_LISTENING:
                break;
        }
        state = STATE_READY;
        Log.d(TAG, "Queueing: STOP LISTENING");

        stopForeground(true);
        stopService(new Intent(this, this.getClass()));

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing: STOP LISTENING");
                if(audioRecord != null)
                    audioRecord.release();
                audioHandler.removeCallbacks(audioReader);
                audioMemory.allocate(0);
            }
        });

    }

    private OutputFormat getOutputFormat() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        return OutputFormat.fromPreference(prefs.getString(OUTPUT_FORMAT_KEY, "WAV"));
    }

    private File getStorageDir() {
        SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        String uriString = prefs.getString(STORAGE_DIRECTORY_URI_KEY, null);
        if (uriString != null) {
            android.net.Uri uri = android.net.Uri.parse(uriString);
            String path = uri.getLastPathSegment();
            if (path != null) {
                String resolved = path.replace("primary:", Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
                File dir = new File(resolved);
                if (!dir.exists()) dir.mkdirs();
                return dir;
            }
        }
        File dir;
        if (isExternalStorageWritable()) {
            dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Echo");
        } else {
            dir = new File(getFilesDir(), "Echo");
        }
        if (!dir.exists()) dir.mkdir();
        return dir;
    }

    public void dumpRecording(final float memorySeconds, final WavFileReceiver wavFileReceiver, String newFileName) {
        if(state != STATE_LISTENING) throw new IllegalStateException("Not listening!");

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                int prependBytes = (int)(memorySeconds * FILL_RATE);
                int bytesAvailable = audioMemory.countFilled();

                int skipBytes = Math.max(0, bytesAvailable - prependBytes);

                int useBytes = bytesAvailable - skipBytes;
                long millis  = System.currentTimeMillis() - 1000 * useBytes / FILL_RATE;
                final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                final String dateTime = DateUtils.formatDateTime(SaidItService.this, millis, flags);
                final OutputFormat outputFormat = getOutputFormat();
                String filename = "Echo - " + dateTime + "." + outputFormat.extension;
                if(!newFileName.equals("")){
                    filename = newFileName + "." + outputFormat.extension;
                }
                filename = filename.replaceAll("[:\\\\/*?\"<>|]", ".");

                File storageDir = getStorageDir();
                File file = new File(storageDir, filename);

                try (AudioFileWriter writer = AudioFileWriterFactory.create(outputFormat, SAMPLE_RATE, file)) {
                    try {
                        audioMemory.read(skipBytes, new AudioMemory.Consumer() {
                            @Override
                            public int consume(byte[] array, int offset, int count) throws IOException {
                                writer.write(array, offset, count);
                                return 0;
                            }
                        });
                    } catch (IOException e) {
                        showToast(getString(R.string.error_during_writing_history_into) + file.getAbsolutePath());
                        Log.e(TAG, "Error during writing history into " + file.getAbsolutePath(), e);
                    }
                    if (wavFileReceiver != null) {
                        wavFileReceiver.fileReady(file, writer.getTotalSampleBytesWritten() * getBytesToSeconds());
                    }
                } catch (IOException e) {
                    showToast(getString(R.string.cant_create_file) + file.getAbsolutePath());
                    Log.e(TAG, "Can't create file " + file.getAbsolutePath(), e);
                }
            }
        });

    }
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    private void showToast(String message) {
        Toast.makeText(SaidItService.this, message, Toast.LENGTH_LONG).show();
    }

    public void startRecording(final float prependedMemorySeconds) {
        switch(state) {
            case STATE_READY:
                innerStartListening();
                break;
            case STATE_LISTENING:
                break;
            case STATE_RECORDING:
                return;
        }
        state = STATE_RECORDING;

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                int prependBytes = (int)(prependedMemorySeconds * FILL_RATE);
                int bytesAvailable = audioMemory.countFilled();

                int skipBytes = Math.max(0, bytesAvailable - prependBytes);

                int useBytes = bytesAvailable - skipBytes;
                long millis  = System.currentTimeMillis() - 1000 * useBytes / FILL_RATE;
                final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
                final String dateTime = DateUtils.formatDateTime(SaidItService.this, millis, flags);
                final OutputFormat outputFormat = getOutputFormat();
                String filename = "Echo - " + dateTime + "." + outputFormat.extension;
                filename = filename.replaceAll("[:\\\\/*?\"<>|]", ".");

                File storageDir = getStorageDir();
                final String storagePath = storageDir.getAbsolutePath();

                String path = storagePath + "/" + filename;

                outputFile = new File(path);
                try {
                    audioFileWriter = AudioFileWriterFactory.create(outputFormat, SAMPLE_RATE, outputFile);
                } catch (IOException e) {
                    final String errorMessage = getString(R.string.cant_create_file) + path;
                    Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                    Log.e(TAG, errorMessage, e);
                    return;
                }

                final String finalPath = path;

                if(skipBytes < bytesAvailable) {
                    try {
                        audioMemory.read(skipBytes, new AudioMemory.Consumer() {
                            @Override
                            public int consume(byte[] array, int offset, int count) throws IOException {
                                audioFileWriter.write(array, offset, count);
                                return 0;
                            }
                        });
                    } catch (IOException e) {
                        final String errorMessage = getString(R.string.error_during_writing_history_into) + finalPath;
                        Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, errorMessage, e);
                        stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
                    }
                }
            }
        });

    }

    public long getMemorySize() {
        return audioMemory.getAllocatedMemorySize();
    }

    public void setMemorySize(final long memorySize) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putLong(AUDIO_MEMORY_SIZE_KEY, memorySize).commit();

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            audioHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioMemory.allocate(memorySize);
                }
            });
        }
    }

    public int getSamplingRate() {
        return SAMPLE_RATE;
    }

    public void setSampleRate(int sampleRate) {
        switch(state) {
            case STATE_READY:
            case STATE_RECORDING:
                return;
            case STATE_LISTENING:
                break;
        }

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).commit();

        innerStopListening();
        SAMPLE_RATE = sampleRate;
        FILL_RATE = 2 * SAMPLE_RATE;
        innerStartListening();
    }

    public interface WavFileReceiver {
        public void fileReady(File file, float runtime);
    }

    public void stopRecording(final WavFileReceiver wavFileReceiver, String newFileName) {
        switch(state) {
            case STATE_READY:
            case STATE_LISTENING:
                return;
            case STATE_RECORDING:
                break;
        }
        state = STATE_LISTENING;

        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                try {
                    audioFileWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "CLOSING ERROR", e);
                }
                if(wavFileReceiver != null) {
                    wavFileReceiver.fileReady(outputFile, audioFileWriter.getTotalSampleBytesWritten() * getBytesToSeconds());
                }
                audioFileWriter = null;
            }
        });

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if(!preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            innerStopListening();
        }

        stopForeground(true);
    }

    private void flushAudioRecord() {
        // Only allowed on the audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        audioHandler.removeCallbacks(audioReader); // remove any delayed callbacks
        audioReader.run();
    }

    final AudioMemory.Consumer filler = new AudioMemory.Consumer() {
        @Override
        public int consume(final byte[] array, final int offset, final int count) throws IOException {
//            Log.d(TAG, "READING " + count + " B");
            final int read = audioRecord.read(array, offset, count, AudioRecord.READ_NON_BLOCKING);
            if (read == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "AUDIO RECORD ERROR - BAD VALUE");
                return 0;
            }
            if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                Log.e(TAG, "AUDIO RECORD ERROR - INVALID OPERATION");
                return 0;
            }
            if (read == AudioRecord.ERROR) {
                Log.e(TAG, "AUDIO RECORD ERROR - UNKNOWN ERROR");
                return 0;
            }
            if (read > 0) {
                int samplesPerBar = read / 2 / AMPLITUDE_BUFFER_SIZE;
                if (samplesPerBar > 0) {
                    for (int bar = 0; bar < AMPLITUDE_BUFFER_SIZE; bar++) {
                        long sumSquares = 0;
                        int start = offset + bar * samplesPerBar * 2;
                        for (int s = 0; s < samplesPerBar; s++) {
                            int idx = start + s * 2;
                            short sample = (short) ((array[idx] & 0xff) | (array[idx + 1] << 8));
                            sumSquares += (long) sample * sample;
                        }
                        float rms = (float) Math.sqrt((double) sumSquares / samplesPerBar) / 32768f;
                        synchronized (amplitudeBuffer) {
                            amplitudeBuffer[amplitudeIndex] = rms;
                            amplitudeIndex = (amplitudeIndex + 1) % AMPLITUDE_BUFFER_SIZE;
                        }
                    }
                }
            }
            if (audioFileWriter != null && read > 0) {
                audioFileWriter.write(array, offset, read);
            }
            if (read == count) {
                // We've filled the buffer, so let's read again.
                audioHandler.post(audioReader);
            } else {
                // It seems we've read everything!
                //
                // Estimate how long do we have until audioRecord fills up completely and post the callback 1 second before that
                // (but not earlier than half the buffer and no later than 90% of the buffer).
                float bufferSizeInSeconds = audioRecord.getBufferSizeInFrames() / (float)SAMPLE_RATE;
                float delaySeconds = bufferSizeInSeconds - 1;
                delaySeconds = Math.max(delaySeconds, bufferSizeInSeconds * 0.5f);
                delaySeconds = Math.min(delaySeconds, bufferSizeInSeconds * 0.9f);
                audioHandler.postDelayed(audioReader, (long)(delaySeconds * 1000));
            }
            return read;
        }
    };
    final Runnable audioReader = new Runnable() {
        @Override
        public void run() {
            try {
                audioMemory.fill(filler);
            } catch (IOException e) {
                final String errorMessage = getString(R.string.error_during_recording_into) + outputFile.getName();
                Toast.makeText(SaidItService.this, errorMessage, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorMessage, e);
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this), "");
            }
        }
    };

    public interface StateCallback {
        public void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded);
    }

    public void getState(final StateCallback stateCallback) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final boolean listeningEnabled = preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true);
        final boolean recording = (state == STATE_RECORDING);
        final Handler sourceHandler = new Handler();
        // Note that we may not run this for quite a while, if audioReader decides to read a lot of audio!
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                flushAudioRecord();
                final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);
                
                int recorded = 0;
                if(audioFileWriter != null) {
                    recorded += audioFileWriter.getTotalSampleBytesWritten();
                    recorded += stats.estimation;
                }
                final float bytesToSeconds = getBytesToSeconds();
                final int finalRecorded = recorded;
                sourceHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stateCallback.state(listeningEnabled, recording,
                                (stats.overwriting ? stats.total : stats.filled + stats.estimation) * bytesToSeconds,
                                stats.total * bytesToSeconds,
                                finalRecorded * bytesToSeconds);
                    }
                });
            }
        });
    }

    public void getAmplitudes(float[] out) {
        synchronized (amplitudeBuffer) {
            for (int i = 0; i < AMPLITUDE_BUFFER_SIZE; i++) {
                out[i] = amplitudeBuffer[(amplitudeIndex + i) % AMPLITUDE_BUFFER_SIZE];
            }
        }
    }

    public float getBytesToSeconds() {
        return 1f / FILL_RATE;
    }

    class BackgroundRecorderBinder extends Binder {
        public SaidItService getService() {
            return SaidItService.this;
        }
    }

    private static final String ACTION_SCHEDULE_CHECK = "eu.mrogalski.saidit.SCHEDULE_CHECK";
    private static final int SCHEDULE_CHECK_REQUEST_CODE = 2;
    private static final long SCHEDULE_CHECK_INTERVAL_MS = 15 * 60 * 1000;

    boolean isWithinSchedule() {
        final SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
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

    public void applySchedule() {
        final SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean listeningEnabled = prefs.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true);

        if (!listeningEnabled) {
            return;
        }

        if (isWithinSchedule()) {
            if (state == STATE_READY) {
                innerStartListening();
            }
        } else {
            if (state == STATE_LISTENING) {
                innerStopListening();
            }
        }

        scheduleNextCheck();
    }

    private void scheduleNextCheck() {
        final SharedPreferences prefs = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(SCHEDULE_ENABLED_KEY, false)) {
            cancelScheduleCheck();
            return;
        }

        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_SCHEDULE_CHECK);
        PendingIntent pendingIntent = PendingIntent.getService(this, SCHEDULE_CHECK_REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + SCHEDULE_CHECK_INTERVAL_MS, pendingIntent);
    }

    private void cancelScheduleCheck() {
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_SCHEDULE_CHECK);
        PendingIntent pendingIntent = PendingIntent.getService(this, SCHEDULE_CHECK_REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        if (intent != null && ACTION_SCHEDULE_CHECK.equals(intent.getAction())) {
            applySchedule();
        }
        return START_STICKY;
    }

    // Workaround for bug where recent app removal caused service to stop
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT| PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, SaidItActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.recording))
                .setSmallIcon(R.drawable.ic_stat_notify_recording)
                .setTicker(getString(R.string.recording))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        // Create the notification channel
        NotificationChannel channel = new NotificationChannel(
                YOUR_NOTIFICATION_CHANNEL_ID,
                "Recording Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        return notificationBuilder.build();
    }

}
