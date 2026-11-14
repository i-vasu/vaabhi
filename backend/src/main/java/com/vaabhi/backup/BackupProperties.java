package com.vaabhi.backup;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nightly backups of the books: a dump of the direct (non-pooled) database URL, into an Object-Locked S3 bucket.
 * Off until the URL, bucket and keys are set; the app runs normally without them.
 */
@ConfigurationProperties("vaabhi.backup")
record BackupProperties(
        boolean enabled,
        @Nullable String databaseUrl,
        @Nullable String databaseUser,
        @Nullable String databasePassword,
        String s3Endpoint,
        String s3Region,
        @Nullable String s3Bucket,
        @Nullable String s3AccessKeyId,
        @Nullable String s3SecretAccessKey,
        String locationLabel,
        String pgDumpPath,
        String pgRestorePath,
        @Nullable String alertPhone) {

    BackupProperties {
        s3Endpoint = blankToDefault(s3Endpoint, "https://s3.ap-south-1.amazonaws.com");
        s3Region = blankToDefault(s3Region, "ap-south-1");
        locationLabel = blankToDefault(locationLabel, "AWS S3, Mumbai (ap-south-1)");
        pgDumpPath = blankToDefault(pgDumpPath, "pg_dump");
        pgRestorePath = blankToDefault(pgRestorePath, "pg_restore");
    }

    boolean configured() {
        return enabled
                && notBlank(databaseUrl)
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
