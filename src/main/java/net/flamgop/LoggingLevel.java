package net.flamgop;

public enum LoggingLevel {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARNING("W"),
    ERROR("E"),
    FATAL("F"),
    SILENT("S")

    ;

    final String adbId;

    LoggingLevel(String adbId) {
        this.adbId = adbId;
    }

    public String adbId() {
        return adbId;
    }
}
