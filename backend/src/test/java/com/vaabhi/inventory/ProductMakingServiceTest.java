package com.vaabhi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductMakingServiceTest {

    @Mock
    private JdbcClient jdbc;

    @Mock
    private InventoryService inventory;

    /** Every fluent call (sql, param, update) returns this same spec; query(Class) is routed by type below. */
    @Mock(answer = Answers.RETURNS_SELF)
    private StatementSpec spec;

    private final Map<Class<?>, MappedQuerySpec<?>> queries = new HashMap<>();

    private ProductMakingService service;

    @BeforeEach
    void setUp() {
        service = new ProductMakingService(jdbc, inventory);
        when(jdbc.sql(anyString())).thenReturn(spec);
        doAnswer(invocation -> {
                    Class<?> type = invocation.getArgument(0);
                    return mapped(type);
                })
                .when(spec)
                .query(any(Class.class));
    }

    @Test
    void getAddsUpMaterialsAndWork() {
        productExists();
        when(mapped(ProductMaking.ProductMaterial.class).list()).thenReturn(List.of(material()));
        when(mapped(ProductMaking.ProductWork.class).list()).thenReturn(List.of(work()));

        var result = service.get(7L);

        assertThat(result.materials()).containsExactly(material());
        assertThat(result.work()).containsExactly(work());
        assertThat(result.costRupees()).isEqualByComparingTo("450.00");
    }

    @Test
    void getRejectsUnknownProduct() {
        productMissing();
        assertRejected(() -> service.get(7L), HttpStatus.NOT_FOUND, "Product not found");
    }

    @Test
    void addMaterialTakesStockOutAndLinksTheMovement() {
        productExists();
        var form = new ProductMaking.ProductMaterialForm(2L, new BigDecimal("2.5"), "  Kurta  ");
        var expected =
                new StockMovement.StockMovementForm(StockMovement.Kind.OUT, new BigDecimal("2.5"), null, "Kurta");
        when(inventory.move(2L, expected, "ravi")).thenReturn(movement());
        when(mapped(ProductMaking.ProductMaterial.class).list()).thenReturn(List.of(material()));

        var result = service.addMaterial(7L, form, "ravi");

        assertThat(result.materials()).containsExactly(material());
        verify(spec).param("item", 2L);
        verify(spec).param("quantity", new BigDecimal("2.5"));
        verify(spec).param("movement", 77L);
        verify(spec).param("note", "Kurta");
        verify(spec).param("by", "ravi");
        verify(spec).update();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void addMaterialNamesTheProductWhenThereIsNoNote(String note) {
        productExists();
        var form = new ProductMaking.ProductMaterialForm(2L, new BigDecimal("2.5"), note);
        var expected = new StockMovement.StockMovementForm(
                StockMovement.Kind.OUT, new BigDecimal("2.5"), null, "Used for Silk saree");
        when(inventory.move(2L, expected, "ravi")).thenReturn(movement());

        service.addMaterial(7L, form, "ravi");

        verify(inventory).move(2L, expected, "ravi");
    }

    @Test
    void addMaterialCapsTheMovementNoteAt200Characters() {
        productExists();
        var expected = new StockMovement.StockMovementForm(
                StockMovement.Kind.OUT, new BigDecimal("2.5"), null, "n".repeat(200));
        when(inventory.move(2L, expected, "ravi")).thenReturn(movement());
        var form = new ProductMaking.ProductMaterialForm(2L, new BigDecimal("2.5"), "n".repeat(250));

        service.addMaterial(7L, form, "ravi");

        verify(inventory).move(2L, expected, "ravi");
    }

    @Test
    void addMaterialRejectsUnknownProductBeforeTouchingStock() {
        productMissing();
        var form = new ProductMaking.ProductMaterialForm(2L, new BigDecimal("2.5"), null);
        assertRejected(() -> service.addMaterial(7L, form, "ravi"), HttpStatus.NOT_FOUND, "Product not found");
        verifyNoInteractions(inventory);
    }

    @Test
    void removeMaterialDeletesTheLinkAndItsStockMovement() {
        when(mapped(Long.class).optional()).thenReturn(Optional.of(77L));
        productExists();

        service.removeMaterial(7L, 5L);

        verify(spec).param("id", 5L);
        verify(spec).param("id", 77L);
        verify(spec).update();
    }

    @Test
    void removeMaterialRejectsUnknownLink() {
        when(mapped(Long.class).optional()).thenReturn(Optional.empty());
        assertRejected(() -> service.removeMaterial(7L, 5L), HttpStatus.NOT_FOUND, "Material not found");
        verify(spec, never()).update();
    }

    @Test
    void addWorkRecordsWorkByAStaffMember() {
        productExists();
        when(mapped(Long.class).single()).thenReturn(1L);
        when(mapped(ProductMaking.ProductWork.class).list()).thenReturn(List.of(work()));
        var form = new ProductMaking.ProductWorkForm(
                3L, "  Meera ", " Stitching ", new BigDecimal("150.00"), LocalDate.of(2026, 11, 1), null);

        var result = service.addWork(7L, form);

        assertThat(result.work()).containsExactly(work());
        verify(spec).param("staff", 3L);
        verify(spec).param("worker", "Meera");
        verify(spec).param("type", "Stitching");
        verify(spec).param("cost", new BigDecimal("150.00"));
        verify(spec).update();
    }

    @Test
    void addWorkWithoutStaffSkipsTheStaffCheck() {
        productExists();
        var form = new ProductMaking.ProductWorkForm(null, "Ravi", "Embroidery", new BigDecimal("80"), null, null);

        service.addWork(7L, form);

        verify(spec, never()).query(Long.class);
        verify(spec).update();
    }

    @Test
    void addWorkRejectsUnknownStaffMember() {
        productExists();
        when(mapped(Long.class).single()).thenReturn(0L);
        var form = new ProductMaking.ProductWorkForm(9L, "Meera", "Stitching", new BigDecimal("150"), null, null);
        assertRejected(
                () -> service.addWork(7L, form),
                HttpStatus.BAD_REQUEST,
                "That staff member does not exist");
        verify(spec, never()).update();
    }

    @Test
    void addWorkRejectsUnknownProduct() {
        productMissing();
        var form = new ProductMaking.ProductWorkForm(null, "Meera", "Stitching", new BigDecimal("150"), null, null);
        assertRejected(() -> service.addWork(7L, form), HttpStatus.NOT_FOUND, "Product not found");
    }

    @Test
    void removeWorkDeletesTheEntry() {
        productExists();
        when(spec.update()).thenReturn(1);

        service.removeWork(7L, 4L);

        verify(spec).param("id", 4L);
        verify(spec).update();
    }

    @Test
    void removeWorkRejectsUnknownEntry() {
        when(spec.update()).thenReturn(0);
        assertRejected(() -> service.removeWork(7L, 4L), HttpStatus.NOT_FOUND, "Work entry not found");
    }

    @Test
    void usedInListsProductsMadeFromTheStock() {
        var usage = new ProductMaking.StockUsedIn(
                7L,
                "Silk saree",
                "silk-saree",
                "DRAFT",
                new BigDecimal("2.5"),
                OffsetDateTime.parse("2026-11-01T10:00:00Z"));
        when(mapped(ProductMaking.StockUsedIn.class).list()).thenReturn(List.of(usage));

        assertThat(service.usedIn(3L)).containsExactly(usage);
        verify(inventory).get(3L);
    }

    @SuppressWarnings("unchecked")
    private <T> MappedQuerySpec<T> mapped(Class<T> type) {
        return (MappedQuerySpec<T>) queries.computeIfAbsent(type, key -> mock(MappedQuerySpec.class));
    }

    private void productExists() {
        when(mapped(String.class).optional()).thenReturn(Optional.of("Silk saree"));
    }

    private void productMissing() {
        when(mapped(String.class).optional()).thenReturn(Optional.empty());
    }

    private static void assertRejected(ThrowingCallable call, HttpStatus status, String reason) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).isEqualTo(reason);
        });
    }

    private static ProductMaking.ProductMaterial material() {
        return new ProductMaking.ProductMaterial(
                1L,
                2L,
                "Kalamkari",
                StockItem.Unit.M,
                new BigDecimal("2.50"),
                new BigDecimal("300.00"),
                "Mill Co",
                "Cotton",
                120,
                60,
                null,
                OffsetDateTime.parse("2026-10-31T10:00:00Z"));
    }

    private static ProductMaking.ProductWork work() {
        return new ProductMaking.ProductWork(
                4L, 3L, "Meera", "Stitching", new BigDecimal("150.00"), LocalDate.of(2026, 11, 1), null);
    }

    private static StockMovement movement() {
        return new StockMovement(
                77L,
                2L,
                "Kalamkari",
                StockMovement.Kind.OUT,
                new BigDecimal("-2.50"),
                BigDecimal.TEN,
                null,
                null,
                "Kurta",
                "ravi",
                OffsetDateTime.parse("2026-11-01T10:00:00Z"));
    }
}
