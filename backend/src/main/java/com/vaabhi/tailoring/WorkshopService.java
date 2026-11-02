package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;
import com.vaabhi.settings.ShopListService;
import com.vaabhi.staff.StaffDirectory;
import com.vaabhi.staff.StaffProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The workshop: the work board (open items by stage), who works on what, which stages each person may work, and
 * the overview of load and deadlines. Stage moves go through {@link OrderService} so order status and events stay
 * in one place.
 */
@Service
public class WorkshopService {

    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final ShopListService lists;
    private final StaffDirectory directory;
    private final OrderService orders;
    private final Clock clock;

    WorkshopService(JdbcClient jdbc, ShopListService lists, StaffDirectory staff, OrderService orders, Clock clock) {
        this.jdbc = jdbc;
        this.lists = lists;
        this.directory = staff;
        this.orders = orders;
        this.clock = clock;
    }

    // --- board --------------------------------------------------------------------------------------------------

    private record ItemRow(
            long itemId,
            long orderId,
            String orderNumber,
            long clientId,
            String firstName,
            @Nullable String lastName,
            String description,
            int quantity,
            @Nullable String colour,
            @Nullable String garment,
            @Nullable String clothPhotoKey,
            long stageId,
            String stageName,
            ProductionStage.Kind stageKind,
            boolean stageActive,
            OffsetDateTime stageSince,
            LocalDate deliveryDate,
            @Nullable LocalDate trialDate,
            @Nullable Long assignedStaffId,
            @Nullable String assignedStaffName) {}

    /** Items of open orders that are not yet handed over. */
    private static final String OPEN_ITEMS = """
            SELECT i.id AS item_id, o.id AS order_id, o.number AS order_number, c.id AS client_id, c.first_name,
                   c.last_name, i.description, i.quantity, i.colour, t.name AS garment, i.cloth_photo_key,
                   st.id AS stage_id, st.name AS stage_name, st.kind AS stage_kind, st.active AS stage_active,
                   i.stage_changed_at AS stage_since, o.delivery_date, o.trial_date, i.assigned_staff_id,
                   s.name AS assigned_staff_name
            FROM order_item i
            JOIN shop_order o ON o.id = i.order_id
            JOIN client c ON c.id = o.client_id
            JOIN production_stage st ON st.id = i.stage_id
            LEFT JOIN garment_template t ON t.id = i.template_id
            LEFT JOIN staff_account s ON s.id = i.assigned_staff_id
            WHERE o.status NOT IN ('DELIVERED', 'CANCELLED') AND st.kind <> 'DELIVERED'
            """;

    private List<ItemRow> openItems(@Nullable Long staffId, @Nullable String query) {
        return jdbc.sql(OPEN_ITEMS + """
                          AND (CAST(:staff AS bigint) IS NULL OR i.assigned_staff_id = :staff)
                          AND (CAST(:q AS varchar) IS NULL OR lower(o.number) LIKE :q
                               OR lower(c.first_name || ' ' || coalesce(c.last_name, '')) LIKE :q
                               OR lower(i.description) LIKE :q)
                        ORDER BY o.delivery_date, o.id, i.position
                        """)
                .param("staff", staffId)
                .param("q", ClientService.like(query))
                .query(ItemRow.class)
                .list();
    }

    private WorkItem toItem(ItemRow row, LocalDate today) {
        return new WorkItem(
                row.itemId(),
                row.orderId(),
                row.orderNumber(),
                row.clientId(),
                ClientService.fullName(row.firstName(), row.lastName()),
                row.description(),
                row.quantity(),
                row.colour(),
                row.garment(),
                row.clothPhotoKey(),
                new ProductionStage(row.stageId(), row.stageName(), row.stageKind()),
                row.stageSince(),
                row.deliveryDate(),
                row.trialDate(),
                row.deliveryDate().isEqual(today),
                row.deliveryDate().isBefore(today),
                row.assignedStaffId() == null
                        ? null
                        : new Assignee(row.assignedStaffId(), String.valueOf(row.assignedStaffName())));
    }

    /**
     * Every work and ready stage in order, each with its open items (most urgent first). A stage removed in the
     * settings still shows while items sit in it, after the active ones.
     */
    @Transactional(readOnly = true)
    public List<BoardColumn> board(@Nullable Long staffId, @Nullable String query) {
        var today = LocalDate.now(clock);
        Map<Long, List<WorkItem>> byStage = new LinkedHashMap<>();
        Map<Long, ProductionStage> removed = new LinkedHashMap<>();
        for (var row : openItems(staffId, query)) {
            byStage.computeIfAbsent(row.stageId(), id -> new ArrayList<>()).add(toItem(row, today));
            if (!row.stageActive()) {
                removed.putIfAbsent(
                        row.stageId(), new ProductionStage(row.stageId(), row.stageName(), row.stageKind()));
            }
        }
        var columns = new ArrayList<BoardColumn>();
        for (var stage : lists.stages()) {
            if (stage.kind() != ProductionStage.Kind.DELIVERED) {
                columns.add(new BoardColumn(stage, false, byStage.getOrDefault(stage.id(), List.of())));
            }
        }
        removed.forEach((id, stage) -> columns.add(new BoardColumn(stage, true, byStage.get(id))));
        return columns;
    }

    // --- overview -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WorkshopOverview overview() {
        var today = LocalDate.now(clock);
        var items = openItems(null, null);
        int inProduction = 0;
        int unassigned = 0;
        int ready = 0;
        Map<Long, int[]> perStage = new LinkedHashMap<>();
        for (var row : items) {
            if (row.stageKind() == ProductionStage.Kind.WORK) {
                inProduction += row.quantity();
                if (row.assignedStaffId() == null) {
                    unassigned += row.quantity();
                }
            } else {
                ready += row.quantity();
            }
            var load = perStage.computeIfAbsent(row.stageId(), id -> new int[2]);
            load[0] += row.quantity();
            if (row.deliveryDate().isBefore(today)) {
                load[1] += row.quantity();
            }
        }
        var stageLoad = new ArrayList<StageLoad>();
        for (var stage : lists.stages()) {
            if (stage.kind() != ProductionStage.Kind.DELIVERED) {
                var load = perStage.getOrDefault(stage.id(), new int[2]);
                stageLoad.add(new StageLoad(stage, load[0], load[1]));
            }
        }

        var orderCounts = jdbc.sql("""
                        SELECT COUNT(*) FILTER (WHERE delivery_date = :today) AS due_today,
                               COUNT(*) FILTER (WHERE delivery_date < :today) AS overdue,
                               COUNT(*) FILTER (WHERE trial_date = :today) AS trials_today
                        FROM shop_order WHERE status NOT IN ('DELIVERED', 'CANCELLED')
                        """)
                .param("today", today)
                .query((rs, n) -> new int[] {rs.getInt("due_today"), rs.getInt("overdue"), rs.getInt("trials_today")})
                .single();
        return new WorkshopOverview(
                inProduction, unassigned, ready, orderCounts[0], orderCounts[1], orderCounts[2], stageLoad, staff());
    }

    // --- assigning ----------------------------------------------------------------------------------------------

    /** Gives an open item in a work stage to a staff member allowed to work that stage (or takes it back). */
    @Transactional
    public WorkItem assign(long itemId, @Nullable Long staffId) {
        var today = LocalDate.now(clock);
        var item = jdbc.sql(OPEN_ITEMS + " AND i.id = :id")
                .param(ID, itemId)
                .query(ItemRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That item is not in the workshop (delivered or cancelled?)"));
        if (staffId != null) {
            if (item.stageKind() != ProductionStage.Kind.WORK) {
                throw OrderService.bad("Only items in a work stage can be given to someone");
            }
            var person = directory.findActive(staffId).orElseThrow(() -> OrderService.bad("Unknown staff member"));
            var allowed = stagesOf(staffId);
            if (!allowed.isEmpty() && !allowed.contains(item.stageId())) {
                throw OrderService.bad(person.name() + " does not work " + item.stageName());
            }
        }
        jdbc.sql("""
                        UPDATE order_item SET assigned_staff_id = :staff,
                            assigned_at = CASE WHEN CAST(:staff AS bigint) IS NULL THEN NULL ELSE now() END
                        WHERE id = :id
                        """).param("staff", staffId).param(ID, itemId).update();
        var updated = jdbc.sql(OPEN_ITEMS + " AND i.id = :id")
                .param(ID, itemId)
                .query(ItemRow.class)
                .single();
        return toItem(updated, today);
    }

    /** Moves items from any number of orders to one stage, order by order. */
    @Transactional
    public List<BoardColumn> move(BulkMove move, @Nullable String actor) {
        var ids = new HashSet<>(move.itemIds());
        Map<Long, List<Long>> byOrder = jdbc
                .sql("SELECT id, order_id FROM order_item WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(Long[]::new))
                .query((rs, n) -> new long[] {rs.getLong("id"), rs.getLong("order_id")})
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        pair -> pair[1], LinkedHashMap::new, Collectors.mapping(pair -> pair[0], Collectors.toList())));
        int found = byOrder.values().stream().mapToInt(List::size).sum();
        if (found != ids.size()) {
            throw OrderService.bad("Some of those items no longer exist");
        }
        byOrder.forEach((orderId, itemIds) -> orders.moveItems(orderId, move.stageId(), itemIds, actor));
        return board(null, null);
    }

    // --- who works which stage ----------------------------------------------------------------------------------

    private List<Long> stagesOf(long staffId) {
        return jdbc.sql("SELECT stage_id FROM staff_stage WHERE staff_id = :id ORDER BY stage_id")
                .param(ID, staffId)
                .query(Long.class)
                .list();
    }

    /** Active staff with their stages and how many items each has now. */
    @Transactional(readOnly = true)
    public List<WorkshopStaff> staff() {
        Map<Long, List<Long>> stages = jdbc
                .sql("SELECT staff_id, stage_id FROM staff_stage ORDER BY stage_id")
                .query((rs, n) -> new long[] {rs.getLong("staff_id"), rs.getLong("stage_id")})
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        pair -> pair[0], Collectors.mapping(pair -> pair[1], Collectors.toList())));
        Map<Long, Integer> load =
                jdbc.sql("""
                        SELECT i.assigned_staff_id AS staff_id, COALESCE(SUM(i.quantity), 0) AS items
                        FROM order_item i JOIN shop_order o ON o.id = i.order_id
                        WHERE i.assigned_staff_id IS NOT NULL AND o.status NOT IN ('DELIVERED', 'CANCELLED')
                        GROUP BY i.assigned_staff_id
                        """).query((rs, n) -> Map.entry(rs.getLong("staff_id"), rs.getInt("items"))).list().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return directory.active().stream()
                .map(person -> new WorkshopStaff(
                        person.id(),
                        person.name(),
                        person.role(),
                        stages.getOrDefault(person.id(), List.of()),
                        load.getOrDefault(person.id(), 0)))
                .toList();
    }

    /** Replaces the stages a staff member may work. Only work stages can be chosen. */
    @Transactional
    public WorkshopStaff saveStages(long staffId, StaffStages form) {
        StaffProfile person = directory
                .findActive(staffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        var work = lists.stages().stream()
                .filter(stage -> stage.kind() == ProductionStage.Kind.WORK)
                .collect(Collectors.toMap(ProductionStage::id, Function.identity()));
        if (!work.keySet().containsAll(form.stageIds())) {
            throw OrderService.bad("Only work stages can be chosen");
        }
        jdbc.sql("DELETE FROM staff_stage WHERE staff_id = :id")
                .param(ID, staffId)
                .update();
        for (var stageId : new HashSet<>(form.stageIds())) {
            jdbc.sql("INSERT INTO staff_stage (staff_id, stage_id) VALUES (:staff, :stage)")
                    .param("staff", staffId)
                    .param("stage", stageId)
                    .update();
        }
        return staff().stream().filter(s -> s.id() == person.id()).findFirst().orElseThrow();
    }
}
