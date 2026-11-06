package dev.badr.pocketdrop.storage;

public final class StorageException extends Exception {
    public enum Reason { TOO_BIG, LOW_DISK, BLOCKED, FOLDER, IO }

    private final Reason reason;

    public StorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
