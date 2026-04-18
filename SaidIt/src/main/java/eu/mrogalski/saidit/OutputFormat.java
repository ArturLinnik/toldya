package eu.mrogalski.saidit;

public enum OutputFormat {
    WAV("wav", "audio/wav"),
    FLAC("flac", "audio/flac"),
    OGG("ogg", "audio/ogg"),
    OPUS("opus", "audio/opus");

    public final String extension;
    public final String mimeType;

    OutputFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public static OutputFormat fromPreference(String value) {
        if (value == null) return WAV;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return WAV;
        }
    }
}
