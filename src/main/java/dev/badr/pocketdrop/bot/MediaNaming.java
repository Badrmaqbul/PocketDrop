package dev.badr.pocketdrop.bot;

import dev.badr.pocketdrop.storage.Kind;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Chooses a file name for things Telegram sends without one (photos, voice notes ...). */
final class MediaNaming {
    private MediaNaming() {}

    /** What a gateway reads from a message: the type and the few fields we need. */
    record Raw(String type, String fileId, String fileName, String mime, long size, String title, String performer) {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    static Incoming.Media build(Raw r, long dateSeconds) {
        if (r == null || r.fileId() == null || r.fileId().isEmpty()) return null;
        String stamp = STAMP.format(Instant.ofEpochSecond(dateSeconds).atZone(ZoneId.systemDefault()));
        switch (r.type()) {
            case "document": {
                String name = r.fileName() != null ? r.fileName() : "document_" + stamp + extFromMime(r.mime());
                return new Incoming.Media(r.fileId(), name, Kind.DOCUMENT, r.size());
            }
            case "photo":
                return new Incoming.Media(r.fileId(), "photo_" + stamp + ".jpg", Kind.PHOTO, r.size());
            case "video": {
                String name = r.fileName() != null ? r.fileName() : "video_" + stamp + ".mp4";
                return new Incoming.Media(r.fileId(), name, Kind.VIDEO, r.size());
            }
            case "audio": {
                String name = r.fileName();
                if (name == null) {
                    String base = r.title() == null ? "audio_" + stamp
                            : (r.performer() == null ? r.title() : r.performer() + " - " + r.title());
                    name = base + extFromMime(r.mime() == null ? "audio/mpeg" : r.mime());
                }
                return new Incoming.Media(r.fileId(), name, Kind.AUDIO, r.size());
            }
            case "voice":
                return new Incoming.Media(r.fileId(), "voice_" + stamp + ".ogg", Kind.VOICE, r.size());
            case "video_note":
                return new Incoming.Media(r.fileId(), "videonote_" + stamp + ".mp4", Kind.VIDEO, r.size());
            case "animation": {
                String name = r.fileName() != null ? r.fileName() : "animation_" + stamp + ".mp4";
                return new Incoming.Media(r.fileId(), name, Kind.VIDEO, r.size());
            }
            default:
                return null;
        }
    }

    static String extFromMime(String mime) {
        if (mime == null) return "";
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "text/plain" -> ".txt";
            case "application/zip" -> ".zip";
            default -> "";
        };
    }
}
