package dev.badr.pocketdrop.storage;

/** What kind of thing the user sent. */
public enum Kind {
    PHOTO("photos"), VIDEO("videos"), AUDIO("audio"), VOICE("voice"), DOCUMENT("files");

    private final String folder;

    Kind(String folder) { this.folder = folder; }

    /** Folder name used by the "by type" layout. */
    public String folder() { return folder; }

    public String id() { return name().toLowerCase(); }

    public static Kind of(String id) {
        for (Kind k : values()) if (k.id().equals(id)) return k;
        return DOCUMENT;
    }
}
