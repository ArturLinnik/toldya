package eu.mrogalski.saidit;

import java.io.File;
import java.io.IOException;

import simplesound.pcm.WavAudioFormat;
import simplesound.pcm.WavFileWriter;

public class WavAudioFileWriter implements AudioFileWriter {
    private final WavFileWriter delegate;

    public WavAudioFileWriter(int sampleRate, File file) throws IOException {
        WavAudioFormat format = new WavAudioFormat.Builder().sampleRate(sampleRate).build();
        this.delegate = new WavFileWriter(format, file);
    }

    @Override
    public void write(byte[] data, int offset, int count) throws IOException {
        delegate.write(data, offset, count);
    }

    @Override
    public int getTotalSampleBytesWritten() {
        return delegate.getTotalSampleBytesWritten();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
