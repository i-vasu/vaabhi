package com.vaabhi.catalog;

import com.vaabhi.shared.storage.ObjectStorage;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for R2 in tests: presigned URLs are fake, "uploads" are recorded with {@link #put}. */
class InMemoryObjectStorage implements ObjectStorage {

    final Set<String> objects = ConcurrentHashMap.newKeySet();
    boolean configured = true;

    void put(String key) {
        objects.add(key);
    }

    @Override
    public boolean configured() {
        return configured;
    }

    @Override
    public URI presignPut(String key, Duration validFor) {
        return URI.create("https://r2.test/" + key + "?X-Amz-Expires=" + validFor.toSeconds());
    }

    @Override
    public boolean exists(String key) {
        return objects.contains(key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
