package com.vaabhi.backup;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nightly backups of the books: a dump of the direct (non-pooled) database URL, into an S3-compatible bucket
 * in India. Off until the URL, endpoint, bucket and keys are set; the app runs normally without them.
 */
@ConfigurationProperties("vaabhi.backup")
record BackupProperties(
        boolean enabled,
        @Nullable String databaseUrl,
        @Nullable String databaseUser,
        @Nullable String databasePassword,
        @Nullable String s3Endpoint,
        @Nullable String s3Region,
        @Nullable String s3Bucket,
        @Nullable String s3AccessKeyId,
        @Nullable String s3SecretAccessKey,
        @Nullable BackupLockMode lockMode,
        @Nullable BackupChecksumMode checksum,
        String locationLabel,
        String pgDumpPath,
        String pgRestorePath,
        @Nullable String alertPhone) {

    BackupProperties {
        lockMode = lockMode == null ? BackupLockMode.NONE : lockMode;
        checksum = checksum == null ? BackupChecksumMode.MD5 : checksum;
        locationLabel = blankToDefault(locationLabel, "AceCloud object storage, India");
        pgDumpPath = blankToDefault(pgDumpPath, "pg_dump");
        pgRestorePath = blankToDefault(pgRestorePath, "pg_restore");
    }

    boolean configured() {
        return enabled
                && notBlank(databaseUrl)
                && notBlank(s3Endpoint)
                && notBlank(s3Region)
                && notBlank(s3Bucket)
                && notBlank(s3AccessKeyId)
                && notBlank(s3SecretAccessKey);
    }

    private static String blankToDefault(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
