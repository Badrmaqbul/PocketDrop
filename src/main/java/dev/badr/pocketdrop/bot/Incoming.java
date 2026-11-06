package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.storage.Kind;

/**
 * One message that arrived, in the plain form the bot logic works with (read from Telegram's JSON by
 * HttpGateway).
 */
public record Incoming(long updateId, boolean hasMessage, long fromId, boolean fromIsBot,
                       String firstName, String lastName, String username,
                       long chatId, String chatType, long messageId, String text,
                       String mediaGroupId, Media media) {

    /** A file, photo, video, ... that came with the message. The name is already chosen. */
    public record Media(String fileId, String name, Kind kind, long size) {}

    public boolean isPrivate() { return "private".equals(chatType); }

    /** An update that is not a normal message (edited message, button press, ...). */
    public static Incoming other(long updateId) {
        return new Incoming(updateId, false, 0, false, null, null, null, 0, "", 0, null, null, null);
    }
}
