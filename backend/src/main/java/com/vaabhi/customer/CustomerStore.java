package com.vaabhi.customer;

import com.vaabhi.party.PartyService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL for customers. */
@Component
class CustomerStore {

    private static final String PROFILE = "SELECT id, name, email, phone FROM customer ";

    private final JdbcClient jdbc;
    private final PartyService parties;

    CustomerStore(JdbcClient jdbc, PartyService parties) {
        this.jdbc = jdbc;
        this.parties = parties;
    }

    /** Row id and whether this call created it. */
    record SignIn(long id, boolean created) {}

    private record PartyRow(
            @Nullable Long partyId,
            @Nullable String name,
            @Nullable String email,
            @Nullable String phone,
            @Nullable String addressLine1,
            @Nullable String addressLine2,
            @Nullable String city,
            @Nullable String state,
            @Nullable String pincode) {}

    /**
     * Keeps the customer's party in step: the same party as the shop client with this phone, else its own. Called
     * after every write that changes who the customer is.
     */
    void linkParty(long id) {
        var row = jdbc.sql("""
                        SELECT party_id, name, email, phone, address_line1, address_line2, city, state, pincode
                        FROM customer WHERE id = :id
                        """).param("id", id).query(PartyRow.class).optional().orElse(null);
        if (row == null) {
            return;
        }
        var name = row.name() != null && !row.name().isBlank()
                ? row.name()
                : row.email() != null ? row.email() : row.phone() != null ? row.phone() : "Customer " + id;
        var address = row.addressLine1() == null
                ? null
                : row.addressLine2() == null ? row.addressLine1() : row.addressLine1() + ", " + row.addressLine2();
        long partyId = parties.save(
                row.partyId(),
                new PartyService.Details(
                        name, row.phone(), row.email(), null, address, row.city(), row.state(), row.pincode()),
                PartyService.Role.CUSTOMER);
        jdbc.sql("UPDATE customer SET party_id = :party WHERE id = :id")
                .param("party", partyId)
                .param("id", id)
                .update();
    }

    Optional<CustomerProfile> find(long id) {
        return jdbc.sql(PROFILE + "WHERE id = :id")
                .param("id", id)
                .query(CustomerProfile.class)
                .optional();
    }

    /** Finds or creates the customer for a verified phone number. */
    SignIn signInByPhone(String phone, OffsetDateTime now) {
        // xmax = 0 only for a freshly inserted row.
        var signIn = jdbc.sql("""
                        INSERT INTO customer (phone, created_at, last_login_at) VALUES (:phone, :now, :now)
                        ON CONFLICT (phone) DO UPDATE SET last_login_at = EXCLUDED.last_login_at
                        RETURNING id, (xmax = 0) AS created
                        """)
                .param("phone", phone)
                .param("now", now)
                .query(SignIn.class)
                .single();
        if (signIn.created()) {
            linkParty(signIn.id());
        }
        return signIn;
    }

    Optional<Long> idByGoogleSub(String sub) {
        return jdbc.sql("SELECT id FROM customer WHERE google_sub = :sub")
                .param("sub", sub)
                .query(Long.class)
                .optional();
    }

    Optional<Long> idByEmail(String email) {
        return jdbc.sql("SELECT id FROM customer WHERE email = :email")
                .param("email", email)
                .query(Long.class)
                .optional();
    }

    /** Links Google to an account and fills in a missing name or email; never overwrites what the customer set. */
    void linkGoogle(long id, String sub, @Nullable String email, @Nullable String name, OffsetDateTime now) {
        jdbc.sql("""
                        UPDATE customer
                        SET google_sub = :sub,
                            email = COALESCE(email, CASE WHEN NOT EXISTS (SELECT 1 FROM customer o
                                                     WHERE o.email = CAST(:email AS VARCHAR) AND o.id <> :id)
                                                     THEN CAST(:email AS VARCHAR) END),
                            name = COALESCE(name, CAST(:name AS VARCHAR)),
                            last_login_at = :now
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("sub", sub)
                .param("email", email)
                .param("name", name)
                .param("now", now)
                .update();
        linkParty(id);
    }

    long insertGoogle(String sub, @Nullable String email, @Nullable String name, OffsetDateTime now) {
        long id = jdbc.sql("""
                        INSERT INTO customer (google_sub, email, name, created_at, last_login_at)
                        VALUES (:sub, :email, :name, :now, :now)
                        RETURNING id
                        """)
                .param("sub", sub)
                .param("email", email)
                .param("name", name)
                .param("now", now)
                .query(Long.class)
                .single();
        linkParty(id);
        return id;
    }

    void updateName(long id, String name) {
        jdbc.sql("UPDATE customer SET name = :name WHERE id = :id")
                .param("id", id)
                .param("name", name)
                .update();
        linkParty(id);
    }

    private record DetailsRow(
            @Nullable LocalDate dateOfBirth,
            @Nullable String addressLine1,
            @Nullable String addressLine2,
            @Nullable String city,
            @Nullable String state,
            @Nullable String pincode,
            String measurements) {}

    private static final tools.jackson.databind.json.JsonMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();
    private static final tools.jackson.core.type.TypeReference<java.util.Map<String, java.util.Map<String, String>>>
            MEASUREMENTS = new tools.jackson.core.type.TypeReference<>() {};

    Optional<CustomerDetails> details(long id) {
        return jdbc.sql("""
                        SELECT date_of_birth, address_line1, address_line2, city, state, pincode,
                               measurements::text AS measurements
                        FROM customer WHERE id = :id
                        """)
                .param("id", id)
                .query(DetailsRow.class)
                .optional()
                .map(r -> new CustomerDetails(
                        r.dateOfBirth(),
                        r.addressLine1(),
                        r.addressLine2(),
                        r.city(),
                        r.state(),
                        r.pincode(),
                        JSON.readValue(r.measurements(), MEASUREMENTS)));
    }

    void updateDetails(long id, CustomerDetails details) {
        jdbc.sql("""
                        UPDATE customer SET date_of_birth = :dob, address_line1 = :line1, address_line2 = :line2,
                            city = :city, state = :state, pincode = :pincode,
                            measurements = CAST(:measurements AS jsonb)
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("dob", details.dateOfBirth())
                .param("line1", blank(details.addressLine1()))
                .param("line2", blank(details.addressLine2()))
                .param("city", blank(details.city()))
                .param("state", blank(details.state()))
                .param("pincode", blank(details.pincode()))
                .param("measurements", JSON.writeValueAsString(withoutBlanks(details.measurements())))
                .update();
        linkParty(id);
    }

    /** Drops empty values and garments with nothing filled in, so the stored document holds only answers. */
    private static java.util.Map<String, java.util.Map<String, String>> withoutBlanks(
            java.util.Map<String, java.util.Map<String, String>> measurements) {
        var kept = new java.util.TreeMap<String, java.util.Map<String, String>>();
        measurements.forEach((garment, fields) -> {
            var values = new java.util.TreeMap<String, String>();
            fields.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    values.put(key, value.strip());
                }
            });
            if (!values.isEmpty()) {
                kept.put(garment, values);
            }
        });
        return kept;
    }

    private static @Nullable String blank(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
