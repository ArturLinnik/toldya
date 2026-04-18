package eu.mrogalski.saidit;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FlacAudioFileWriter implements AudioFileWriter {
    private final MediaCodec codec;
    private final FileOutputStream outputStream;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int totalSampleBytesWritten = 0;
    private long presentationTimeUs = 0;
    private final int sampleRate;

    public FlacAudioFileWriter(int sampleRate, File file) throws IOException {
        this.sampleRate = sampleRate;
        outputStream = new FileOutputStream(file);

        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, 1);
        format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5);
        format.setInteger(MediaFormat.KEY_PCM_ENCODING,
                android.media.AudioFormat.ENCODING_PCM_16BIT);

        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
        } catch (Exception e) {
            outputStream.close();
            throw new IOException("Failed to initialize FLAC encoder", e);
        }
    }

    @Override
    public void write(byte[] data, int offset, int count) throws IOException {
        int remaining = count;
        int pos = offset;

        while (remaining > 0) {
            int inputIndex = codec.dequeueInputBuffer(10000);
            if (inputIndex < 0) {
                drainOutput();
                continue;
            }

            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
            int toWrite = Math.min(remaining, inputBuffer.capacity());
            inputBuffer.clear();
            inputBuffer.put(data, pos, toWrite);

            long pts = presentationTimeUs;
            int samples = toWrite / 2;
            presentationTimeUs += (samples * 1_000_000L) / sampleRate;

            codec.queueInputBuffer(inputIndex, 0, toWrite, pts, 0);

            pos += toWrite;
            remaining -= toWrite;
            totalSampleBytesWritten += toWrite;

            drainOutput();
        }
    }

    private void drainOutput() throws IOException {
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex < 0) break;

            ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);

            if (bufferInfo.size > 0) {
                byte[] encoded = new byte[bufferInfo.size];
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.get(encoded);
                outputStream.write(encoded);
            }

            codec.releaseOutputBuffer(outputIndex, false);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
        }
    }

    @Override
    public int getTotalSampleBytesWritten() {
        return totalSampleBytesWritten;
    }

    @Override
    public void close() throws IOException {
        try {
            int inputIndex = codec.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            drainOutput();
        } finally {
            codec.stop();
            codec.release();
            outputStream.close();
        }
    }
}
