package dev.badr.pocketdrop.bot;

import java.io.InputStream;
import java.util.List;

/** Everything the bot needs from Telegram. */
public interface Gateway extends AutoCloseable {

    /** Something went wrong talking to Telegram. code 0 means the network (no answer at all). */
    final class GatewayException extends Exception {
        private final int code;
        private final int retryAfter;

        public GatewayException(int code, String message, int retryAfter) {
            super(message);
            this.code = code;
            this.retryAfter = retryAfter;
        }

        public int code() { return code; }
        public int retryAfter() { return retryAfter; }
        public boolean isNetwork() { return code == 0; }

        /** The library could not be started safely (nothing was sent to Telegram). */
        public boolean isUnavailable() { return code == -1; }
    }

    record FileRef(String path, long size) {}

    /** The bot's @username (also proves the token works). */
    String getMe() throws GatewayException, InterruptedException;

    /** Waits up to the given time for new messages. Offsets are handled inside. */
    List<Incoming> poll(int timeoutSeconds) throws GatewayException, InterruptedException;

    void sendMessage(long chatId, String text) throws GatewayException, InterruptedException;

    void deleteMessage(long chatId, long messageId) throws GatewayException, InterruptedException;

    FileRef getFile(String fileId) throws GatewayException, InterruptedException;

    /** The caller must close the stream. */
    InputStream download(FileRef file) throws GatewayException, InterruptedException;

    void setCommands(List<String[]> commands) throws GatewayException, InterruptedException;

    @Override
    default void close() {}

}
