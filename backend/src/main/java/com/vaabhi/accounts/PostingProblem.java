package com.vaabhi.accounts;

import java.time.OffsetDateTime;

/** A source record the sweeper could not post, with the reason. */
public record PostingProblem(
        String sourceType,
        long sourceId,
        String sourceStep,
        String message,
        OffsetDateTime firstSeen,
        OffsetDateTime lastSeen) {}
