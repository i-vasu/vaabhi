package com.vaabhi.tailoring;

import com.vaabhi.settings.BillingSettings;
import com.vaabhi.settings.DeliveryPackage;
import com.vaabhi.settings.GarmentTemplate;
import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.settings.ProductionStage;
import com.vaabhi.settings.SettingsService;
import com.vaabhi.settings.ShopListService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Booking orders, moving their items through production, delivering, cancelling and taking payments. */
@Service
public class OrderService {

    private static final String ID = "id";
    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final ClientService clients;
    private final ShopListService lists;
    private final SettingsService settings;
    private final NumberingService numbering;
    private final ServiceProductService products;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderService(
            JdbcClient jdbc,
            JsonMapper json,
            ClientService clients,
            ShopListService lists,
            SettingsService settings,
            NumberingService numbering,
            ServiceProductService products,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clients = clients;
        this.lists = lists;
        this.settings = settings;
        this.numbering = numbering;
        this.products = products;
        this.events = events;
        this.clock = clock;
    }

    // --- money --------------------------------------------------------------------------------------------------

    /** Items, delivery charge, discount and total for a booking. */
    record Totals(long itemsPaise, long deliveryPaise, long discountPaise, long totalPaise) {}

    static Totals totals(
            List<OrderItemForm> items,
            long deliveryPaise,
            OrderForm.DiscountKind kind,
            long discountValue,
            BillingSettings.DiscountBase base) {
        long itemsPaise = items.stream()
                .mapToLong(item -> item.pricePaise() * item.quantity())
                .sum();
        long discountBase = base == BillingSettings.DiscountBase.ORDER_TOTAL ? itemsPaise + deliveryPaise : itemsPaise;
        long discount;
        if (kind == OrderForm.DiscountKind.PERCENT) {
            if (discountValue > 10_000) {
                throw bad("A discount cannot be more than 100%");
            }
            discount = Math.round(discountBase * discountValue / 10_000.0);
        } else {
            discount = Math.min(discountValue, discountBase);
        }
        return new Totals(itemsPaise, deliveryPaise, discount, itemsPaise + deliveryPaise - discount);
    }

    // --- create -------------------------------------------------------------------------------------------------

    @Transactional
    public Order create(OrderForm form, @Nullable String actor) {
        var client = clients.summary(form.clientId());
        DeliveryPackage pkg = null;
        if (form.deliveryPackageId() != null) {
            pkg = lists.deliveryPackages().stream()
                    .filter(p -> form.deliveryPackageId().equals(p.id()))
                    .findFirst()
                    .orElseThrow(() -> bad("That delivery package is no longer offered"));
        }
        LocalDate deliveryDate = form.deliveryDate() != null
                ? form.deliveryDate()
                : pkg != null ? form.orderDate().plusDays(pkg.days()) : null;
        if (deliveryDate == null) {
            throw bad("Pick a delivery date");
        }
        if (deliveryDate.isBefore(form.orderDate())) {
            throw bad("The delivery date cannot be before the order date");
        }
        if (form.trialDate() != null
                && (form.trialDate().isBefore(form.orderDate())
                        || form.trialDate().isAfter(deliveryDate))) {
            throw bad("The trial date must fall between the order and delivery dates");
        }
        var templates =
                lists.garmentTemplates().stream().collect(Collectors.toMap(GarmentTemplate::id, Function.identity()));
        for (var item : form.items()) {
            if (item.templateId() != null && !templates.containsKey(item.templateId())) {
                throw bad("Unknown garment for " + item.description());
            }
            if (item.serviceProductId() != null) {
                products.get(item.serviceProductId());
            }
        }

        var totals = totals(
                form.items(),
                pkg == null ? 0 : pkg.pricePaise(),
                form.discountKind(),
                form.discountValue(),
                settings.billing().discountAppliesTo());
        if (form.advancePaise() > totals.totalPaise()) {
            throw bad("The advance cannot be more than the order total");
        }

        var firstStage = lists.stages().getFirst();
        var number = numbering.next(NumberSequence.Kind.ORDER);
        long orderId = jdbc.sql("""
                        INSERT INTO shop_order (number, client_id, order_date, delivery_date, due_time, trial_date,
                            delivery_package_id, delivery_charge_paise, items_paise, discount_kind, discount_value,
                            discount_paise, coupon_code, total_paise, paid_paise, notes, created_by)
                        VALUES (:number, :client, :orderDate, :deliveryDate, :dueTime, :trialDate, :pkg, :delivery,
                            :items, :discountKind, :discountValue, :discount, :coupon, :total, :paid, :notes, :by)
                        RETURNING id
                        """)
                .param("number", number)
                .param("client", form.clientId())
                .param("orderDate", form.orderDate())
                .param("deliveryDate", deliveryDate)
                .param("dueTime", form.dueTime())
                .param("trialDate", form.trialDate())
                .param("pkg", pkg == null ? null : pkg.id())
                .param("delivery", totals.deliveryPaise())
                .param("items", totals.itemsPaise())
                .param("discountKind", form.discountKind().name())
                .param("discountValue", form.discountValue())
                .param("discount", totals.discountPaise())
                .param("coupon", ClientService.blankToNull(form.couponCode()))
                .param("total", totals.totalPaise())
                .param("paid", form.advancePaise())
                .param("notes", ClientService.blankToNull(form.notes()))
                .param("by", actor)
                .query(Long.class)
                .single();

        int position = 0;
        for (var item : form.items()) {
            var template = item.templateId() == null ? null : templates.get(item.templateId());
            jdbc.sql("""
                            INSERT INTO order_item (order_id, position, template_id, service_product_id, description,
                                quantity, price_paise, colour, note, measurements, cloth_photo_key, stage_id)
                            VALUES (:order, :position, :template, :product, :description, :quantity, :price, :colour,
                                :note, CAST(:measurements AS jsonb), :photo, :stage)
                            """)
                    .param("order", orderId)
                    .param("position", ++position)
                    .param("template", item.templateId())
                    .param("product", item.serviceProductId())
                    .param("description", item.description().trim())
                    .param("quantity", item.quantity())
                    .param("price", item.pricePaise())
                    .param("colour", ClientService.blankToNull(item.colour()))
                    .param("note", ClientService.blankToNull(item.note()))
                    .param("measurements", measurementsJson(template, item.measurements()))
                    .param("photo", item.clothPhotoKey())
                    .param("stage", firstStage.id())
                    .update();
        }
        if (form.advancePaise() > 0) {
            insertPayment(orderId, form.advancePaise(), form.advanceMode(), OrderPayment.Kind.ADVANCE, null, actor);
        }
        events.publishEvent(new OrderEvent(
                OrderEvent.Type.PLACED, orderId, number, client.id(), client.name(), totals.totalPaise(), actor));
        return get(orderId);
    }

    /** Keeps only the garment's own fields with a value; null when there is nothing to keep. */
    private @Nullable String measurementsJson(
            @Nullable GarmentTemplate template, @Nullable Map<String, String> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return null;
        }
        Set<String> allowed = template == null
                ? measurements.keySet()
                : template.fields().stream().map(GarmentTemplate.Field::key).collect(Collectors.toSet());
        Map<String, String> kept = new LinkedHashMap<>();
        measurements.forEach((key, value) -> {
            if (allowed.contains(key) && value != null && !value.isBlank()) {
                kept.put(key, value.trim());
            }
        });
        return kept.isEmpty() ? null : json.writeValueAsString(kept);
    }

    // --- read ---------------------------------------------------------------------------------------------------

    private record OrderRow(
            long id,
            String number,
            long clientId,
            OrderStatus status,
            LocalDate orderDate,
            LocalDate deliveryDate,
            @Nullable LocalTime dueTime,
            @Nullable LocalDate trialDate,
            @Nullable Long deliveryPackageId,
            long deliveryChargePaise,
            long itemsPaise,
            OrderForm.DiscountKind discountKind,
            long discountValue,
            long discountPaise,
            @Nullable String couponCode,
            long totalPaise,
            long paidPaise,
            @Nullable String notes,
            @Nullable String createdBy,
            OffsetDateTime createdAt,
            @Nullable OffsetDateTime deliveredAt) {}

    private record ItemRow(
            long id,
            @Nullable Long templateId,
            @Nullable Long serviceProductId,
            String description,
            int quantity,
            long pricePaise,
            @Nullable String colour,
            @Nullable String note,
            @Nullable String measurements,
            @Nullable String clothPhotoKey,
            @Nullable Long stageId,
            OffsetDateTime stageChangedAt) {}

    @Transactional(readOnly = true)
    public Order get(long id) {
        var row = jdbc.sql("SELECT * FROM shop_order WHERE id = :id")
                .param(ID, id)
                .query(OrderRow.class)
                .optional()
                .orElseThrow(OrderService::notFound);
        var stages = allStages();
        var items = jdbc.sql("""
                        SELECT id, template_id, service_product_id, description, quantity, price_paise, colour, note,
                               measurements::text AS measurements, cloth_photo_key, stage_id, stage_changed_at
                        FROM order_item WHERE order_id = :id ORDER BY position
                        """).param(ID, id).query(ItemRow.class).list().stream()
                .map(item -> new OrderItem(
                        item.id(),
                        item.templateId(),
                        item.serviceProductId(),
                        item.description(),
                        item.quantity(),
                        item.pricePaise(),
                        item.colour(),
                        item.note(),
                        item.measurements() == null ? null : json.readValue(item.measurements(), VALUES),
                        item.clothPhotoKey(),
                        item.stageId() == null ? null : stages.get(item.stageId()),
                        item.stageChangedAt()))
                .toList();
        var payments = jdbc.sql("""
                        SELECT id, amount_paise, mode, kind, note, received_by, received_at FROM order_payment
                        WHERE order_id = :id ORDER BY received_at, id
                        """).param(ID, id).query(OrderPayment.class).list();
        return new Order(
                row.id(),
                row.number(),
                clients.summary(row.clientId()),
                row.status(),
                row.orderDate(),
                row.deliveryDate(),
                row.dueTime(),
                row.trialDate(),
                row.deliveryPackageId(),
                row.deliveryChargePaise(),
                row.itemsPaise(),
                row.discountKind(),
                row.discountValue(),
                row.discountPaise(),
                row.couponCode(),
                row.totalPaise(),
                row.paidPaise(),
                row.totalPaise() - row.paidPaise(),
                row.notes(),
                row.createdBy(),
                row.createdAt(),
                row.deliveredAt(),
                items,
                payments);
    }

    /** Every stage ever defined (removed ones too), so old orders still show where their items are. */
    private Map<Long, ProductionStage> allStages() {
        return jdbc.sql("SELECT id, name, kind FROM production_stage").query(ProductionStage.class).list().stream()
                .collect(Collectors.toMap(ProductionStage::id, Function.identity()));
    }

    /** List filter: a status, OVERDUE (open and past due), OPEN (not delivered or cancelled) or ALL. */
    public enum Filter {
        ALL,
        OPEN,
        BOOKED,
        IN_PROGRESS,
        READY,
        DELIVERED,
        CANCELLED,
        OVERDUE
    }

    private record SummaryRow(
            long id,
            String number,
            long clientId,
            String firstName,
            @Nullable String lastName,
            String phone,
            OrderStatus status,
            LocalDate orderDate,
            LocalDate deliveryDate,
            @Nullable LocalDate trialDate,
            int itemCount,
            @Nullable String itemsLabel,
            long totalPaise,
            long paidPaise) {}

    @Transactional(readOnly = true)
    public OrderPage list(
            @Nullable String query,
            Filter filter,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable Long clientId,
            int page,
            int pageSize) {
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
        params.put("open", filter == Filter.OPEN || filter == Filter.OVERDUE);
        params.put("overdue", filter == Filter.OVERDUE);
        params.put("today", today);
        params.put("from", from);
        params.put("to", to);
        params.put("client", clientId);
        var where = """
                FROM shop_order o JOIN client c ON c.id = o.client_id
                WHERE (CAST(:status AS varchar) IS NULL OR o.status = :status)
                  AND (NOT :open OR o.status NOT IN ('DELIVERED', 'CANCELLED'))
                  AND (NOT :overdue OR o.delivery_date < :today)
                  AND (CAST(:from AS date) IS NULL OR o.order_date >= :from)
                  AND (CAST(:to AS date) IS NULL OR o.order_date <= :to)
                  AND (CAST(:client AS bigint) IS NULL OR o.client_id = :client)
                  AND (CAST(:q AS varchar) IS NULL OR lower(o.number) LIKE :q
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
                        SELECT o.id, o.number, o.client_id, c.first_name, c.last_name, c.phone, o.status, o.order_date,
                               o.delivery_date, o.trial_date, o.total_paise, o.paid_paise,
                               (SELECT COALESCE(SUM(i.quantity), 0) FROM order_item i WHERE i.order_id = o.id)
                                   AS item_count,
                               (SELECT string_agg(i.description, ', ' ORDER BY i.position) FROM order_item i
                                WHERE i.order_id = o.id) AS items_label
                        """ + where + " ORDER BY o.order_date DESC, o.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(row -> new OrderSummary(
                        row.id(),
                        row.number(),
                        row.clientId(),
                        ClientService.fullName(row.firstName(), row.lastName()),
                        row.phone(),
                        row.status(),
                        row.orderDate(),
                        row.deliveryDate(),
                        row.trialDate(),
                        row.itemCount(),
                        row.itemsLabel() == null ? "" : row.itemsLabel(),
                        row.totalPaise(),
                        row.totalPaise() - row.paidPaise(),
                        isOpen(row.status()) && row.deliveryDate().isBefore(today)))
                .toList();
        return new OrderPage(items, total, page, pageSize);
    }

    static boolean isOpen(OrderStatus status) {
        return status != OrderStatus.DELIVERED && status != OrderStatus.CANCELLED;
    }

    // --- production ---------------------------------------------------------------------------------------------

    /**
     * Moves items (all of them when {@code itemIds} is empty) to a stage and updates the order's status. Returns
     * the order.
     */
    @Transactional
    public Order moveItems(long orderId, long stageId, Collection<Long> itemIds, @Nullable String actor) {
        var order = get(orderId);
        if (order.status() == OrderStatus.CANCELLED) {
            throw bad("This order was cancelled");
        }
        var stage = lists.stages().stream()
                .filter(s -> s.id() != null && s.id() == stageId)
                .findFirst()
                .orElseThrow(() -> bad("Unknown stage"));
        var targets =
                itemIds.isEmpty() ? order.items().stream().map(OrderItem::id).toList() : List.copyOf(itemIds);
        var known = order.items().stream().map(OrderItem::id).collect(Collectors.toSet());
        if (!known.containsAll(targets)) {
            throw bad("Those items are not on this order");
        }
        jdbc.sql("""
                        UPDATE order_item SET stage_id = :stage, stage_changed_at = now()
                        WHERE order_id = :order AND id = ANY(:items) AND stage_id IS DISTINCT FROM :stage
                        """)
                .param("stage", stage.id())
                .param("order", orderId)
                .param("items", targets.toArray(Long[]::new))
                .update();
        refreshStatus(order, actor);
        return get(orderId);
    }

    @Transactional
    public Order deliver(long orderId, @Nullable String actor) {
        var delivered = lists.stages().stream()
                .filter(stage -> stage.kind() == ProductionStage.Kind.DELIVERED)
                .findFirst()
                .orElseThrow();
        return moveItems(orderId, delivered.id(), List.of(), actor);
    }

    /** Derives the order's status from its items and announces READY / DELIVERED when they are reached. */
    private void refreshStatus(Order before, @Nullable String actor) {
        var stages = lists.stages();
        var kinds = allStages();
        var itemStages = jdbc.sql("SELECT stage_id FROM order_item WHERE order_id = :id")
                .param(ID, before.id())
                .query(Long.class)
                .list();
        var firstStage = stages.getFirst().id();
        OrderStatus status;
        if (itemStages.stream().allMatch(id -> kindOf(kinds, id) == ProductionStage.Kind.DELIVERED)) {
            status = OrderStatus.DELIVERED;
        } else if (itemStages.stream().allMatch(id -> kindOf(kinds, id) != ProductionStage.Kind.WORK)) {
            status = OrderStatus.READY;
        } else if (itemStages.stream().allMatch(id -> id.equals(firstStage))) {
            status = OrderStatus.BOOKED;
        } else {
            status = OrderStatus.IN_PROGRESS;
        }
        if (status == before.status()) {
            return;
        }
        jdbc.sql("""
                        UPDATE shop_order SET status = :status, updated_at = now(),
                            delivered_at = CASE WHEN :status = 'DELIVERED' THEN now() ELSE NULL END
                        WHERE id = :id
                        """).param("status", status.name()).param(ID, before.id()).update();
        var type = switch (status) {
            case READY -> OrderEvent.Type.READY;
            case DELIVERED -> OrderEvent.Type.DELIVERED;
            default -> null;
        };
        if (type != null) {
            events.publishEvent(new OrderEvent(
                    type,
                    before.id(),
                    before.number(),
                    before.client().id(),
                    before.client().name(),
                    before.totalPaise(),
                    actor));
        }
    }

    private static ProductionStage.@Nullable Kind kindOf(Map<Long, ProductionStage> stages, @Nullable Long id) {
        var stage = id == null ? null : stages.get(id);
        return stage == null ? null : stage.kind();
    }

    @Transactional
    public Order cancel(long orderId, @Nullable String actor) {
        var order = get(orderId);
        if (order.status() == OrderStatus.DELIVERED) {
            throw bad("A delivered order cannot be cancelled");
        }
        if (order.status() != OrderStatus.CANCELLED) {
            jdbc.sql("UPDATE shop_order SET status = 'CANCELLED', updated_at = now() WHERE id = :id")
                    .param(ID, orderId)
                    .update();
            events.publishEvent(new OrderEvent(
                    OrderEvent.Type.CANCELLED,
                    orderId,
                    order.number(),
                    order.client().id(),
                    order.client().name(),
                    order.totalPaise(),
                    actor));
        }
        return get(orderId);
    }

    /** Changes dates and notes after booking. */
    @Transactional
    public Order reschedule(long orderId, OrderSchedule schedule) {
        var order = get(orderId);
        if (schedule.deliveryDate().isBefore(order.orderDate())) {
            throw bad("The delivery date cannot be before the order date");
        }
        jdbc.sql("""
                        UPDATE shop_order SET delivery_date = :delivery, due_time = :dueTime, trial_date = :trial,
                            notes = :notes, updated_at = now()
                        WHERE id = :id
                        """)
                .param("delivery", schedule.deliveryDate())
                .param("dueTime", schedule.dueTime())
                .param("trial", schedule.trialDate())
                .param("notes", ClientService.blankToNull(schedule.notes()))
                .param(ID, orderId)
                .update();
        return get(orderId);
    }

    // --- payments -----------------------------------------------------------------------------------------------

    @Transactional
    public Order addPayment(long orderId, PaymentForm form, @Nullable String actor) {
        var order = get(orderId);
        if (order.status() == OrderStatus.CANCELLED && !form.refund()) {
            throw bad("This order was cancelled; only refunds can be recorded");
        }
        long amount = form.refund() ? -form.amountPaise() : form.amountPaise();
        if (form.refund() && form.amountPaise() > order.paidPaise()) {
            throw bad("A refund cannot be more than what was paid");
        }
        insertPayment(
                orderId,
                amount,
                form.mode(),
                form.refund() ? OrderPayment.Kind.REFUND : OrderPayment.Kind.PAYMENT,
                ClientService.blankToNull(form.note()),
                actor);
        jdbc.sql("UPDATE shop_order SET paid_paise = paid_paise + :amount, updated_at = now() WHERE id = :id")
                .param("amount", amount)
                .param(ID, orderId)
                .update();
        events.publishEvent(new OrderEvent(
                OrderEvent.Type.PAYMENT_RECEIVED,
                orderId,
                order.number(),
                order.client().id(),
                order.client().name(),
                amount,
                actor));
        return get(orderId);
    }

    private void insertPayment(
            long orderId,
            long amountPaise,
            PaymentMode mode,
            OrderPayment.Kind kind,
            @Nullable String note,
            @Nullable String actor) {
        jdbc.sql("""
                        INSERT INTO order_payment (order_id, amount_paise, mode, kind, note, received_by)
                        VALUES (:order, :amount, :mode, :kind, :note, :by)
                        """)
                .param("order", orderId)
                .param("amount", amountPaise)
                .param("mode", mode.name())
                .param("kind", kind.name())
                .param("note", note)
                .param("by", actor)
                .update();
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
}
