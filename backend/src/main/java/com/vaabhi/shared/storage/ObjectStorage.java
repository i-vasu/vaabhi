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

    /** Uploads a small object from the server (e.g. a copied Instagram cover); throws when storage refuses it. */
    default void put(String key, byte[] bytes, String contentType) {
        throw new UnsupportedOperationException("Server uploads are not supported here");
    }

    /** Downloads an object from the server (e.g. a photo rendition to convert); throws when it is missing. */
    default byte[] get(String key) {
        throw new UnsupportedOperationException("Server downloads are not supported here");
    }

    /** Best effort: a failure is logged, not thrown, because the database row is already gone. */
    void delete(String key);
}
