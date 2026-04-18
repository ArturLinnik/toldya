package eu.mrogalski.saidit;

import java.io.Closeable;
import java.io.IOException;

public interface AudioFileWriter extends Closeable {
    void write(byte[] data, int offset, int count) throws IOException;
    int getTotalSampleBytesWritten();
}
