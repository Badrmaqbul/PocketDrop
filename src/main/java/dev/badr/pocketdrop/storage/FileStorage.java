package dev.badr.pocketdrop.storage;

import dev.badr.pocketdrop.core.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Decides where files go and writes them safely (never over an existing file, never outside the folder). */
public final class FileStorage {
    public record Saved(Path path, long size) {}

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    public Path baseDir(Config c) throws StorageException {
        if (c.saveDir == null || c.saveDir.isBlank()) {
            throw new StorageException(StorageException.Reason.FOLDER, "No save folder is set");
        }
        return Path.of(c.saveDir).toAbsolutePath().normalize();
    }

    public Path targetDir(Config c, Kind kind, LocalDate day) throws StorageException {
        Path base = baseDir(c);
        String photos = FileNamer.sanitize(c.photosFolderName, "photos");
        Path dir = base;
        switch (c.layout) {
            case "type" -> dir = base.resolve(kind.folder());
            case "date" -> {
                dir = base.resolve(DAY.format(day));
                if (kind == Kind.PHOTO && c.photosSubfolder) dir = dir.resolve(photos);
            }
            default -> {
                if (kind == Kind.PHOTO && c.photosSubfolder) dir = base.resolve(photos);
            }
        }
        dir = dir.normalize();
        if (!dir.startsWith(base)) throw new StorageException(StorageException.Reason.FOLDER, "Bad folder");
        return dir;
    }

    /** Free bytes on the drive that holds the save folder (works even if the folder does not exist yet). */
    public long usableSpace(Path dir) throws IOException {
        Path p = dir;
        while (p != null && !Files.exists(p)) p = p.getParent();
        if (p == null) throw new IOException("Drive not found");
        FileStore store = Files.getFileStore(p);
        return store.getUsableSpace();
    }

    public Saved save(Config c, Kind kind, String safeName, InputStream in, LocalDate day)
            throws StorageException, IOException {
        Path base = baseDir(c);
        Path dir = targetDir(c, kind, day);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.FOLDER, "Cannot create folder", e);
        }

        // 1) write everything to a temporary ".part" file; 2) only when complete, move it to its final name.
        //    So a half-downloaded file is never visible under its real name, and an existing file is never replaced.
        long limit = c.maxFileMb * 1024L * 1024L;
        long total = 0;
        Path tmp = Files.createTempFile(dir, ".pocketdrop-", ".part");
        boolean moved = false;
        Path target = null;
        try {
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > limit) throw new StorageException(StorageException.Reason.TOO_BIG, "File too big");
                    out.write(buf, 0, r);
                }
            }
            for (int n = 1; n <= 10_000 && !moved; n++) {
                String candidate = n == 1 ? safeName : FileNamer.withSuffix(safeName, n, c.dupStyle);
                Path p = dir.resolve(candidate).normalize();
                if (!p.startsWith(base)) throw new StorageException(StorageException.Reason.FOLDER, "Name escapes folder");
                try {
                    Files.move(tmp, p); // fails if the name is taken: we then try the next number
                    target = p;
                    moved = true;
                } catch (FileAlreadyExistsException exists) {
                    // try the next number
                }
            }
            if (!moved) throw new StorageException(StorageException.Reason.IO, "Too many files with this name");
        } finally {
            if (!moved) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
        return new Saved(target, total);
    }
}
