package eu.mrogalski.saidit;

import java.io.File;
import java.io.IOException;

public class AudioFileWriterFactory {
    public static AudioFileWriter create(OutputFormat format, int sampleRate, File file) throws IOException {
        switch (format) {
            case FLAC: return new FlacAudioFileWriter(sampleRate, file);
            case OGG:
            case OPUS: return new OpusAudioFileWriter(sampleRate, file);
            case WAV:
            default:   return new WavAudioFileWriter(sampleRate, file);
        }
    }
}
