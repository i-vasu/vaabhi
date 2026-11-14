package com.vaabhi.backup;

import org.jspecify.annotations.Nullable;

/** Size and SHA-256 of an object in the backup bucket, as S3 reports them (checksum in Base64). */
public record S3ObjectInfo(long sizeBytes, @Nullable String sha256Base64) {}
