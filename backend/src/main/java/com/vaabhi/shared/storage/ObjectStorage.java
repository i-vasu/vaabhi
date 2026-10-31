package com.vaabhi.shared.storage;

import java.net.URI;
import java.time.Duration;

/** The few object-storage operations the app needs. Implemented by {@link R2ObjectStorage}. */
public interface ObjectStorage {

    /** False when credentials are missing (local development); callers should refuse uploads politely. */
    boolean configured();

    /** A URL the browser can PUT the object to directly, valid for {@code validFor}. */
    URI presignPut(String key, Duration validFor);

    boolean exists(String key);

    /** Best effort: a failure is logged, not thrown, because the database row is already gone. */
    void delete(String key);
}
