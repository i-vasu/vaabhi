package com.vaabhi.party;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the party list in step with the modules that know people: a shop client, a website customer, a supplier
 * typed on a stock item. One party per phone; the same phone in two places is the same party with two roles.
 */
@Service
public class PartyService {

    /** How the shop deals with a party. A party can have several roles. */
    public enum Role {
        CUSTOMER,
        SUPPLIER,
        JOB_WORKER
    }

    /** What the caller knows about the party; null fields leave what is stored. */
    public record Details(
            String name,
            @Nullable String phone,
            @Nullable String email,
            @Nullable String gstin,
            @Nullable String address,
            @Nullable String city,
            @Nullable String state,
            @Nullable String pinCode) {

        public static Details of(String name, @Nullable String phone) {
            return new Details(name, phone, null, null, null, null, null, null);
        }
    }

    private record Existing(
            long id, @Nullable String phone, boolean isCustomer, boolean isSupplier, boolean isJobWorker) {

        /** True when the party has a role other than {@code role}: then its name belongs to that other role. */
        boolean hasOtherRole(Role role) {
            return (isCustomer && role != Role.CUSTOMER)
                    || (isSupplier && role != Role.SUPPLIER)
                    || (isJobWorker && role != Role.JOB_WORKER);
        }
    }

    private static final String EXISTING = "SELECT id, phone, is_customer, is_supplier, is_job_worker FROM party";

    private final JdbcClient jdbc;

    PartyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds the party (by phone, else {@code current}, else for suppliers and job workers by name) or creates it,
     * gives it the role and stores the details given. Joins the caller's transaction. Returns the party's id.
     */
    @Transactional
    public long save(@Nullable Long current, Details details, Role role) {
        var phone = phone(details.phone());
        var name = details.name().trim().isEmpty()
                ? (phone == null ? "Unnamed" : phone)
                : details.name().trim();
        Existing existing = null;
        if (phone != null) {
            existing = find(" WHERE phone = :key", phone);
        }
        if (existing == null && current != null) {
            existing = find(" WHERE id = :key", current);
        }
        if (existing == null && phone == null && role != Role.CUSTOMER) {
            existing = find(
                    " WHERE lower(name) = lower(:key) AND phone IS NULL AND is_"
                            + (role == Role.SUPPLIER ? "supplier" : "job_worker") + " ORDER BY id LIMIT 1",
                    name);
        }
        if (existing == null) {
            return jdbc.sql("""
                            INSERT INTO party (name, phone, email, gstin, address, city, state, pin_code,
                                is_customer, is_supplier, is_job_worker)
                            VALUES (:name, :phone, :email, :gstin, :address, :city, :state, :pinCode,
                                :customer, :supplier, :jobWorker)
                            RETURNING id
                            """)
                    .params(params(details, name, phone, role))
                    .query(Long.class)
                    .single();
        }
        jdbc.sql("""
                        UPDATE party SET
                            name = CASE WHEN :keepName THEN name ELSE :name END,
                            phone = COALESCE(phone, :phone),
                            email = COALESCE(:email, email), gstin = COALESCE(:gstin, gstin),
                            address = COALESCE(:address, address), city = COALESCE(:city, city),
                            state = COALESCE(:state, state), pin_code = COALESCE(:pinCode, pin_code),
                            is_customer = is_customer OR :customer, is_supplier = is_supplier OR :supplier,
                            is_job_worker = is_job_worker OR :jobWorker
                        WHERE id = :id
                        """)
                .params(params(details, name, existing.phone() == null ? phone : null, role))
                // A customer's name is not overwritten by the shop name typed for them as a supplier, and so on.
                .param("keepName", existing.hasOtherRole(role))
                .param("id", existing.id())
                .update();
        return existing.id();
    }

    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    private @Nullable Existing find(String where, Object key) {
        return jdbc.sql(EXISTING + where)
                .param("key", key)
                .query(Existing.class)
                .optional()
                .orElse(null);
    }

    private static Map<String, @Nullable Object> params(Details d, String name, @Nullable String phone, Role role) {
        var params = new java.util.HashMap<String, @Nullable Object>();
        params.put("name", name.length() <= 160 ? name : name.substring(0, 160));
        params.put("phone", phone);
        params.put("email", blankToNull(d.email()));
        params.put("gstin", blankToNull(d.gstin()));
        params.put("address", blankToNull(d.address()));
        params.put("city", blankToNull(d.city()));
        params.put("state", blankToNull(d.state()));
        params.put("pinCode", blankToNull(d.pinCode()));
        params.put("customer", role == Role.CUSTOMER);
        params.put("supplier", role == Role.SUPPLIER);
        params.put("jobWorker", role == Role.JOB_WORKER);
        return params;
    }

    /**
     * A phone as stored on parties: E.164 (+…) kept as digits, Indian numbers as +91XXXXXXXXXX; null when it is not
     * a usable phone. Matches the backfill in V29.
     */
    public static @Nullable String phone(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var digits = raw.replaceAll("[^0-9]", "");
        if (raw.strip().startsWith("+") && digits.length() >= 8 && digits.length() <= 15) {
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return null;
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
