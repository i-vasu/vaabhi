package com.vaabhi.backup;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Temp files that only the backend's own user can read or write. Dumps and bill attachments are business data, and
 * the shared temp directory is readable by every user on the machine.
 */
final class PrivateTempFiles {

    private PrivateTempFiles() {}

    static Path create(String prefix, String suffix) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempFile(
                    prefix, suffix, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        // Not POSIX (a Windows dev machine): restrict the file to its owner after creating it.
        var file = Files.createTempFile(prefix, suffix).toFile(); // NOSONAR java:S5443 - owner-only just below
        boolean restricted = file.setReadable(true, true) && file.setWritable(true, true) && file.setExecutable(false);
        if (!restricted) {
            Files.deleteIfExists(file.toPath());
            throw new IOException("Could not make a private temp file");
        }
        return file.toPath();
    }
}
