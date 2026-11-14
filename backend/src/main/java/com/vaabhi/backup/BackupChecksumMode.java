package com.vaabhi.backup;

/** Which integrity header the backup store sends with an upload. */
public enum BackupChecksumMode {
    /** {@code Content-MD5}, accepted by every S3-compatible store (AceCloud default). */
    MD5,
    /** {@code x-amz-checksum-sha256}, the AWS object-checksum header. */
    SHA256
}
