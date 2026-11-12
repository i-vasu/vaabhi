package com.vaabhi.tailoring;

import com.vaabhi.party.PartyService;
import com.vaabhi.settings.GarmentTemplate;
import com.vaabhi.settings.ShopListService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Shop customers and their measurements. Every statement is static; all values are bind parameters. */
@Service
public class ClientService {

    private static final String ID = "id";
    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>() {};

    /** Orders that still count towards what a customer owes or has spent. */
    static final String LIVE_ORDER = "status <> 'CANCELLED'";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final ShopListService lists;
    private final PartyService parties;

    ClientService(JdbcClient jdbc, JsonMapper json, ShopListService lists, PartyService parties) {
        this.jdbc = jdbc;
        this.json = json;
        this.lists = lists;
        this.parties = parties;
    }

    /** Keeps the client's party (one per phone, shared with a website customer) in step with the client. */
    private void linkParty(long clientId, ClientForm form) {
        var current = jdbc.sql("SELECT party_id FROM client WHERE id = :id")
                .param(ID, clientId)
                .query(Long.class)
                .optional()
                .orElse(null);
        var name = form.firstName().trim()
                + (blankToNull(form.lastName()) == null
                        ? ""
                        : " " + form.lastName().trim());
        long partyId = parties.save(
                current,
                new PartyService.Details(
                        name,
                        Phones.normalize(form.phone()),
                        form.email(),
                        form.gstin(),
                        form.address(),
                        form.city(),
                        form.state(),
                        form.pinCode()),
                PartyService.Role.CUSTOMER);
        jdbc.sql("UPDATE client SET party_id = :party WHERE id = :id")
                .param("party", partyId)
                .param(ID, clientId)
                .update();
    }

    // --- list ---------------------------------------------------------------------------------------------------

    public enum Sort {
        RECENT,
        NAME,
        VALUE,
        OWING
    }

    private static final String STATS = """
            SELECT c.id, c.first_name, c.last_name, c.phone, c.photo_key, c.client_type, c.created_at,
                   COUNT(o.id) AS order_count,
                   COALESCE(SUM(o.total_rupees), 0) AS spent_rupees,
                   COALESCE(SUM(GREATEST(o.total_rupees - o.paid_rupees, 0)), 0) AS outstanding_rupees,
                   MAX(o.order_date) AS last_order_date
            FROM client c
            LEFT JOIN shop_order o ON o.client_id = c.id AND o.status <> 'CANCELLED'
            """;

    private record StatsRow(
            long id,
            String firstName,
            @Nullable String lastName,
            String phone,
            @Nullable String photoKey,
            ClientForm.ClientType clientType,
            OffsetDateTime createdAt,
            int orderCount,
            BigDecimal spentRupees,
            BigDecimal outstandingRupees,
            @Nullable LocalDate lastOrderDate) {

        ClientSummary summary() {
            return new ClientSummary(
                    id,
                    fullName(firstName, lastName),
                    phone,
                    photoKey,
                    clientType,
                    orderCount,
                    spentRupees,
                    outstandingRupees,
                    lastOrderDate);
        }
    }

    static String fullName(String first, @Nullable String last) {
        return last == null || last.isBlank() ? first : first + " " + last;
    }

    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    @Transactional(readOnly = true)
    public ClientPage list(@Nullable String query, Sort sort, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", like(query));
        params.put("digits", digits(query));
        var where = """
                WHERE (CAST(:q AS varchar) IS NULL
                       OR lower(c.first_name || ' ' || coalesce(c.last_name, '')) LIKE :q
                       OR (CAST(:digits AS varchar) IS NOT NULL AND c.phone LIKE :digits))
                """;
        long total = jdbc.sql("SELECT COUNT(*) FROM client c " + where)
                .params(params)
                .query(Long.class)
                .single();
        var order = switch (sort) {
            case RECENT -> "COALESCE(MAX(o.order_date), c.created_at::date) DESC, c.id DESC";
            case NAME -> "lower(c.first_name), lower(c.last_name), c.id";
            case VALUE -> "spent_rupees DESC, c.id DESC";
            case OWING -> "outstanding_rupees DESC, c.id DESC";
        };
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);
        var items = jdbc
                .sql(STATS + where + " GROUP BY c.id ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query(StatsRow.class)
                .list()
                .stream()
                .map(StatsRow::summary)
                .toList();
        return new ClientPage(items, total, page, pageSize);
    }

    static @Nullable String like(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%"
                + query.trim()
                        .toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
    }

    /** Digits of a phone-like query (at least 3), matched anywhere in the stored number. */
    static @Nullable String digits(@Nullable String query) {
        if (query == null) {
            return null;
        }
        var digits = query.replaceAll("[^0-9]", "");
        return digits.length() >= 3 ? "%" + digits + "%" : null;
    }

    // --- read ---------------------------------------------------------------------------------------------------

    private record ClientRow(
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
            OffsetDateTime createdAt) {}

    @Transactional(readOnly = true)
    public Client get(long id) {
        var row = jdbc.sql("SELECT * FROM client WHERE id = :id")
                .param(ID, id)
                .query(ClientRow.class)
                .optional()
                .orElseThrow(ClientService::notFound);
        var stats = jdbc.sql(STATS + " WHERE c.id = :id GROUP BY c.id")
                .param(ID, id)
                .query(StatsRow.class)
                .single();
        return new Client(
                row.id(),
                row.phone(),
                row.firstName(),
                row.lastName(),
                row.gender(),
                row.photoKey(),
                row.email(),
                row.altPhone(),
                row.whatsapp(),
                row.address(),
                row.city(),
                row.state(),
                row.pinCode(),
                row.clientType(),
                row.gstin(),
                row.preferredStyle(),
                row.birthDate(),
                row.occasionName(),
                row.occasionDate(),
                row.createdAt(),
                stats.orderCount(),
                stats.spentRupees(),
                stats.outstandingRupees(),
                measurements(id));
    }

    /** Name and phone for documents and messages. */
    @Transactional(readOnly = true)
    public ClientSummary summary(long id) {
        return jdbc.sql(STATS + " WHERE c.id = :id GROUP BY c.id")
                .param(ID, id)
                .query(StatsRow.class)
                .optional()
                .map(StatsRow::summary)
                .orElseThrow(ClientService::notFound);
    }

    // --- write --------------------------------------------------------------------------------------------------

    @Transactional
    public Client create(ClientForm form) {
        try {
            long id = jdbc.sql("""
                            INSERT INTO client (phone, first_name, last_name, gender, photo_key, email, alt_phone,
                                whatsapp, address, city, state, pin_code, client_type, gstin, preferred_style,
                                birth_date, occasion_name, occasion_date)
                            VALUES (:phone, :firstName, :lastName, :gender, :photoKey, :email, :altPhone, :whatsapp,
                                :address, :city, :state, :pinCode, :clientType, :gstin, :preferredStyle, :birthDate,
                                :occasionName, :occasionDate)
                            RETURNING id
                            """).params(params(form)).query(Long.class).single();
            linkParty(id, form);
            return get(id);
        } catch (DuplicateKeyException e) {
            throw duplicatePhone(e);
        }
    }

    @Transactional
    public Client update(long id, ClientForm form) {
        try {
            var params = params(form);
            params.put(ID, id);
            int updated = jdbc.sql("""
                            UPDATE client SET phone = :phone, first_name = :firstName, last_name = :lastName,
                                gender = :gender, photo_key = :photoKey, email = :email, alt_phone = :altPhone,
                                whatsapp = :whatsapp, address = :address, city = :city, state = :state,
                                pin_code = :pinCode, client_type = :clientType, gstin = :gstin,
                                preferred_style = :preferredStyle, birth_date = :birthDate,
                                occasion_name = :occasionName, occasion_date = :occasionDate, updated_at = now()
                            WHERE id = :id
                            """).params(params).update();
            if (updated == 0) {
                throw notFound();
            }
            linkParty(id, form);
            return get(id);
        } catch (DuplicateKeyException e) {
            throw duplicatePhone(e);
        }
    }

    private static Map<String, Object> params(ClientForm form) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("phone", Phones.normalize(form.phone()));
        params.put("firstName", form.firstName().trim());
        params.put("lastName", blankToNull(form.lastName()));
        params.put("gender", form.gender().name());
        params.put("photoKey", form.photoKey());
        params.put("email", blankToNull(form.email()));
        params.put("altPhone", Phones.normalizeOptional(form.altPhone()));
        params.put("whatsapp", Phones.normalizeOptional(form.whatsapp()));
        params.put("address", blankToNull(form.address()));
        params.put("city", blankToNull(form.city()));
        params.put("state", blankToNull(form.state()));
        params.put("pinCode", blankToNull(form.pinCode()));
        params.put("clientType", form.clientType().name());
        params.put("gstin", blankToNull(form.gstin()));
        params.put("preferredStyle", blankToNull(form.preferredStyle()));
        params.put("birthDate", form.birthDate());
        params.put("occasionName", blankToNull(form.occasionName()));
        params.put("occasionDate", form.occasionDate());
        return params;
    }

    static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // --- measurements -------------------------------------------------------------------------------------------

    private record MeasurementRow(
            long id,
            long templateId,
            String garment,
            String values,
            @Nullable String notes,
            @Nullable String takenBy,
            OffsetDateTime takenAt) {}

    /** All measurements, newest first. */
    List<Measurement> measurements(long clientId) {
        return jdbc.sql("""
                        SELECT m.id, m.template_id, t.name AS garment, m.values::text AS values, m.notes, m.taken_by,
                               m.taken_at
                        FROM client_measurement m JOIN garment_template t ON t.id = m.template_id
                        WHERE m.client_id = :id ORDER BY m.taken_at DESC, m.id DESC
                        """).param(ID, clientId).query(MeasurementRow.class).list().stream()
                .map(row -> new Measurement(
                        row.id(),
                        row.templateId(),
                        row.garment(),
                        json.readValue(row.values(), VALUES),
                        row.notes(),
                        row.takenBy(),
                        row.takenAt()))
                .toList();
    }

    @Transactional
    public Measurement addMeasurement(long clientId, MeasurementForm form, @Nullable String takenBy) {
        requireExists(clientId);
        GarmentTemplate template = lists.garmentTemplate(form.templateId());
        Set<String> allowed =
                template.fields().stream().map(GarmentTemplate.Field::key).collect(Collectors.toSet());
        var unknown = form.values().keySet().stream()
                .filter(key -> !allowed.contains(key))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Not a " + template.name() + " measurement: " + String.join(", ", unknown));
        }
        Map<String, String> values = new LinkedHashMap<>();
        form.values().forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                values.put(key, value.trim());
            }
        });
        long id = jdbc.sql("""
                        INSERT INTO client_measurement (client_id, template_id, values, notes, taken_by)
                        VALUES (:clientId, :templateId, CAST(:values AS jsonb), :notes, :takenBy) RETURNING id
                        """)
                .param("clientId", clientId)
                .param("templateId", form.templateId())
                .param("values", json.writeValueAsString(values))
                .param("notes", blankToNull(form.notes()))
                .param("takenBy", takenBy)
                .query(Long.class)
                .single();
        return measurements(clientId).stream()
                .filter(m -> m.id() == id)
                .findFirst()
                .orElseThrow();
    }

    void requireExists(long clientId) {
        if (jdbc.sql("SELECT COUNT(*) FROM client WHERE id = :id")
                        .param(ID, clientId)
                        .query(Long.class)
                        .single()
                == 0) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
    }

    private static ResponseStatusException duplicatePhone(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "A customer with this mobile number already exists", cause);
    }
}
