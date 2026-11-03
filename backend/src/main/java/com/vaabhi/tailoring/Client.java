package com.vaabhi.tailoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A shop customer with their measurements and money position.
 *
 * @param outstandingRupees what open orders still owe
 */
public record Client(
        long id,
        String phone,
        String firstName,
        @Nullable String lastName,
        ClientForm.Gender gender,
        @Nullable String photoKey,
        @Nullable String email,
        @Nullable String altPhone,
        @Nullable String whatsapp,
        @Nullable String address,
        @Nullable String city,
        @Nullable String state,
        @Nullable String pinCode,
        ClientForm.ClientType clientType,
        @Nullable String gstin,
        @Nullable String preferredStyle,
        @Nullable LocalDate birthDate,
        @Nullable String occasionName,
        @Nullable LocalDate occasionDate,
        OffsetDateTime createdAt,
        int orderCount,
        BigDecimal spentRupees,
        BigDecimal outstandingRupees,
        List<Measurement> measurements) {}
