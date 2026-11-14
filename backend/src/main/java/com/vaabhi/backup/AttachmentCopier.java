package com.vaabhi.backup;

import com.vaabhi.shared.storage.ObjectStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Copies bill attachments from R2 into the backup bucket once each. One bad file is noted on the run and retried
 * on a later night; it never fails the database backup.
 */
@Component
class AttachmentCopier {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCopier.class);

    private final ObjectStorage objects;
    private final S3BackupStore store;
    private final JdbcClient jdbc;

    AttachmentCopier(ObjectStorage objects, S3BackupStore store, JdbcClient jdbc) {
        this.objects = objects;
        this.store = store;
        this.jdbc = jdbc;
    }

    AttachmentCopyResult copyNew() {
        var bills = jdbc.sql("""
                        SELECT attachment_key
                        FROM supplier_bill
                        WHERE attachment_key IS NOT NULL
                          AND attachment_key NOT IN (SELECT source_key FROM backup_file)
                        ORDER BY id
                        """)
                .query(String.class)
                .list();
        var copied = 0;
        var problems = new ArrayList<String>();
        for (var sourceKey : bills) {
            if (copy(sourceKey, problems)) {
                copied++;
            }
        }
        return new AttachmentCopyResult(copied, problems);
    }

    private boolean copy(String sourceKey, List<String> problems) {
        Path temp = null;
        try (var in = objects.open(sourceKey)) {
            temp = Files.createTempFile("vaabhi-attachment-", ".bin");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            var sha = BackupService.sha256Hex(temp);
            var size = Files.size(temp);
            var backupKey = BackupKeys.attachment(sourceKey);
            store.put(backupKey, temp, sha, BackupKeys.longRetainUntil(Instant.now()));
            jdbc.sql("""
                            INSERT INTO backup_file (source_key, backup_key, size_bytes, sha256)
                            VALUES (:sourceKey, :backupKey, :size, :sha)
                            """)
                    .param("sourceKey", sourceKey)
                    .param("backupKey", backupKey)
                    .param("size", size)
                    .param("sha", sha)
                    .update();
            return true;
        } catch (NoSuchFileException e) {
            problems.add(sourceKey + ": missing in R2");
            return false;
        } catch (Exception e) {
            problems.add(sourceKey + ": " + BackupService.failureOf(e));
            return false;
        } finally {
            delete(temp);
        }
    }

    private static void delete(@Nullable Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("Could not delete the temporary attachment {}", temp, e);
        }
    }
}
