package com.vaabhi.tailoring;

import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alterations: garments brought back for a fit fix, length change, repair or rework. They skip the production
 * stages and move BOOKED → IN_PROGRESS → READY → DELIVERED by hand.
 */
@Service
public class AlterationService {

    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final ClientService clients;
    private final SettingsService settings;
    private final NumberingService numbering;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AlterationService(
            JdbcClient jdbc,
            ClientService clients,
            SettingsService settings,
            NumberingService numbering,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.settings = settings;
        this.numbering = numbering;
        this.events = events;
        this.clock = clock;
    }

    // --- create -------------------------------------------------------------------------------------------------

    @Transactional
    public Alteration create(AlterationForm form, @Nullable String actor) {
        var client = clients.summary(form.clientId());
        if (form.orderId() != null) {
            var owner = jdbc.sql("SELECT client_id FROM shop_order WHERE id = :id")
                    .param(ID, form.orderId())
                    .query(Long.class)
                    .optional()
                    .orElseThrow(() -> OrderService.bad("That order does not exist"));
            if (owner != form.clientId().longValue()) {
                throw OrderService.bad("That order belongs to another customer");
            }
        }
        if (form.readyBy().isBefore(LocalDate.now(clock))) {
            throw OrderService.bad("The ready-by date cannot be in the past");
        }
        long total = form.paid()
                ? form.items().stream().mapToLong(AlterationItemForm::costPaise).sum()
                : 0;
        var number = numbering.next(NumberSequence.Kind.ALTERATION);
        long id = jdbc.sql("""
                        INSERT INTO alteration (number, client_id, order_id, ready_by, paid, total_paise, notes,
                            undertaking_accepted, created_by)
                        VALUES (:number, :client, :order, :readyBy, :paid, :total, :notes, TRUE, :by)
                        RETURNING id
                        """)
                .param("number", number)
                .param("client", form.clientId())
                .param("order", form.orderId())
                .param("readyBy", form.readyBy())
                .param("paid", form.paid())
                .param("total", total)
                .param("notes", ClientService.blankToNull(form.notes()))
                .param("by", actor)
                .query(Long.class)
                .single();
        int position = 0;
        for (var item : form.items()) {
            jdbc.sql("""
                            INSERT INTO alteration_item (alteration_id, position, kind, garment, instructions,
                                photo_key, cost_paise)
                            VALUES (:alteration, :position, :kind, :garment, :instructions, :photo, :cost)
                            """)
                    .param("alteration", id)
                    .param("position", ++position)
                    .param("kind", item.kind().name())
                    .param("garment", item.garment().trim())
                    .param("instructions", ClientService.blankToNull(item.instructions()))
                    .param("photo", item.photoKey())
                    .param("cost", form.paid() ? item.costPaise() : 0)
                    .update();
        }
        events.publishEvent(
                new AlterationEvent(AlterationEvent.Type.BOOKED, id, number, client.id(), client.name(), total, actor));
        return get(id);
    }

    // --- read ---------------------------------------------------------------------------------------------------

    private record Row(
            long id,
            String number,
            long clientId,
            @Nullable Long orderId,
            @Nullable String orderNumber,
            OrderStatus status,
            LocalDate readyBy,
            boolean paid,
            long totalPaise,
            long collectedPaise,
            @Nullable String notes,
            @Nullable String createdBy,
            OffsetDateTime createdAt,
            @Nullable OffsetDateTime deliveredAt) {}

    @Transactional(readOnly = true)
    public Alteration get(long id) {
        var row = jdbc.sql("""
                        SELECT a.id, a.number, a.client_id, a.order_id, o.number AS order_number, a.status,
                               a.ready_by, a.paid, a.total_paise, a.collected_paise, a.notes, a.created_by,
                               a.created_at, a.delivered_at
                        FROM alteration a LEFT JOIN shop_order o ON o.id = a.order_id
                        WHERE a.id = :id
                        """).param(ID, id).query(Row.class).optional().orElseThrow(AlterationService::notFound);
        var items = jdbc.sql("""
                        SELECT id, kind, garment, instructions, photo_key, cost_paise FROM alteration_item
                        WHERE alteration_id = :id ORDER BY position
                        """).param(ID, id).query(AlterationItem.class).list();
        return new Alteration(
                row.id(),
                row.number(),
                clients.summary(row.clientId()),
                row.orderId(),
                row.orderNumber(),
                row.status(),
                row.readyBy(),
                row.paid(),
                row.totalPaise(),
                row.collectedPaise(),
                row.totalPaise() - row.collectedPaise(),
                row.notes(),
                settings.documents().undertaking(),
                row.createdBy(),
                row.createdAt(),
                row.deliveredAt(),
                items);
    }

    private record SummaryRow(
            long id,
            String number,
            long clientId,
            String firstName,
            @Nullable String lastName,
            String phone,
            OrderStatus status,
            LocalDate readyBy,
            int itemCount,
            @Nullable String itemsLabel,
            boolean paid,
            long totalPaise,
            long collectedPaise) {}

    /** Same filters as the orders list. */
    @Transactional(readOnly = true)
    public AlterationPage list(
            @Nullable String query, OrderService.Filter filter, @Nullable Long clientId, int page, int pageSize) {
        var today = LocalDate.now(clock);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", ClientService.like(query));
        params.put("digits", ClientService.digits(query));
        params.put(
                "status",
                switch (filter) {
                    case BOOKED, IN_PROGRESS, READY, DELIVERED, CANCELLED -> filter.name();
                    default -> null;
                });
        params.put(
                "open",
                filter == OrderService.Filter.OPEN
                        || filter == OrderService.Filter.OVERDUE
                        || filter == OrderService.Filter.DUE_TODAY);
        params.put("overdue", filter == OrderService.Filter.OVERDUE);
        params.put("dueToday", filter == OrderService.Filter.DUE_TODAY);
        params.put("today", today);
        params.put("client", clientId);
        var where = """
                FROM alteration a JOIN client c ON c.id = a.client_id
                WHERE (CAST(:status AS varchar) IS NULL OR a.status = :status)
                  AND (NOT :open OR a.status NOT IN ('DELIVERED', 'CANCELLED'))
                  AND (NOT :overdue OR a.ready_by < :today)
                  AND (NOT :dueToday OR a.ready_by = :today)
                  AND (CAST(:client AS bigint) IS NULL OR a.client_id = :client)
                  AND (CAST(:q AS varchar) IS NULL OR lower(a.number) LIKE :q
                       OR lower(c.first_name || ' ' || coalesce(c.last_name, '')) LIKE :q
                       OR (CAST(:digits AS varchar) IS NOT NULL AND c.phone LIKE :digits))
                """;
        long total = jdbc.sql("SELECT COUNT(*) " + where)
                .params(params)
                .query(Long.class)
                .single();
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);
        var items = jdbc
                .sql("""
                        SELECT a.id, a.number, a.client_id, c.first_name, c.last_name, c.phone, a.status, a.ready_by,
                               a.paid, a.total_paise, a.collected_paise,
                               (SELECT COUNT(*) FROM alteration_item i WHERE i.alteration_id = a.id) AS item_count,
                               (SELECT string_agg(i.garment, ', ' ORDER BY i.position) FROM alteration_item i
                                WHERE i.alteration_id = a.id) AS items_label
                        """ + where + " ORDER BY a.ready_by, a.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(row -> new AlterationSummary(
                        row.id(),
                        row.number(),
                        row.clientId(),
                        ClientService.fullName(row.firstName(), row.lastName()),
                        row.phone(),
                        row.status(),
                        row.readyBy(),
                        row.itemCount(),
                        row.itemsLabel() == null ? "" : row.itemsLabel(),
                        row.paid(),
                        row.totalPaise(),
                        row.totalPaise() - row.collectedPaise(),
                        OrderService.isOpen(row.status()) && row.readyBy().isBefore(today)))
                .toList();
        return new AlterationPage(items, total, page, pageSize);
    }

    // --- progress -----------------------------------------------------------------------------------------------

    /** Moves forward (or cancels) and records money collected. Delivered and cancelled alterations are final. */
    @Transactional
    public Alteration progress(long id, AlterationProgress progress, @Nullable String actor) {
        var before = get(id);
        var target = progress.status();
        if (!OrderService.isOpen(before.status())) {
            if (target != before.status() || progress.collectPaise() > 0) {
                throw OrderService.bad(
                        before.status() == OrderStatus.CANCELLED
                                ? "This alteration was cancelled"
                                : "This alteration was already delivered");
            }
            return before;
        }
        if (target != OrderStatus.CANCELLED
                && target.ordinal() < before.status().ordinal()) {
            throw OrderService.bad(
                    "An alteration cannot move back to " + target.name().toLowerCase());
        }
        if (target == OrderStatus.CANCELLED && progress.collectPaise() > 0) {
            throw OrderService.bad("Nothing can be collected on a cancelled alteration");
        }
        if (progress.collectPaise() > before.balancePaise()) {
            throw OrderService.bad("That is more than what is due");
        }
        jdbc.sql("""
                        UPDATE alteration SET status = :status, collected_paise = collected_paise + :collect,
                            delivered_at = CASE WHEN :status = 'DELIVERED' THEN now() ELSE delivered_at END
                        WHERE id = :id
                        """)
                .param("status", target.name())
                .param("collect", progress.collectPaise())
                .param(ID, id)
                .update();
        var client = before.client();
        if (progress.collectPaise() > 0) {
            jdbc.sql("""
                            INSERT INTO alteration_payment (alteration_id, amount_paise, mode, received_by)
                            VALUES (:id, :amount, :mode, :by)
                            """)
                    .param(ID, id)
                    .param("amount", progress.collectPaise())
                    .param("mode", (progress.mode() == null ? PaymentMode.CASH : progress.mode()).name())
                    .param("by", actor)
                    .update();
            events.publishEvent(new AlterationEvent(
                    AlterationEvent.Type.PAYMENT_RECEIVED,
                    id,
                    before.number(),
                    client.id(),
                    client.name(),
                    progress.collectPaise(),
                    actor));
        }
        var type = target == before.status()
                ? null
                : switch (target) {
                    case READY -> AlterationEvent.Type.READY;
                    case DELIVERED -> AlterationEvent.Type.DELIVERED;
                    case CANCELLED -> AlterationEvent.Type.CANCELLED;
                    default -> null;
                };
        if (type != null) {
            events.publishEvent(new AlterationEvent(
                    type, id, before.number(), client.id(), client.name(), before.totalPaise(), actor));
        }
        return get(id);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Alteration not found");
    }
}
