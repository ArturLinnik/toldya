package eu.mrogalski.saidit;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class OpusAudioFileWriter implements AudioFileWriter {
    private final MediaCodec codec;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex = -1;
    private boolean muxerStarted = false;
    private int totalSampleBytesWritten = 0;
    private long presentationTimeUs = 0;
    private final int sampleRate;

    private static final int BIT_RATE = 64000;

    private static final int[] SUPPORTED_RATES = { 8000, 12000, 16000, 24000, 48000 };

    public OpusAudioFileWriter(int sampleRate, File file) throws IOException {
        this.sampleRate = nearestSupportedRate(sampleRate);

        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS, this.sampleRate, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);

        try {
            muxer = new MediaMuxer(file.getAbsolutePath(),
                    android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
        } catch (Exception e) {
            throw new IOException("Failed to initialize Opus encoder", e);
        }
    }

    private static int nearestSupportedRate(int rate) {
        int best = SUPPORTED_RATES[0];
        int bestDiff = Math.abs(rate - best);
        for (int supported : SUPPORTED_RATES) {
            int diff = Math.abs(rate - supported);
            if (diff < bestDiff) {
                best = supported;
                bestDiff = diff;
            }
        }
        return best;
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

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
                continue;
            }

            if (outputIndex < 0) break;

            ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                codec.releaseOutputBuffer(outputIndex, false);
                continue;
            }

            if (muxerStarted && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
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
            if (muxerStarted) {
                muxer.stop();
            }
            muxer.release();
        }
    }
}
