package com.vaabhi.money;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Money the shop spends: rent, salaries, material, bills. */
@Service
public class ExpenseService {

    private static final String ID = "id";
    private static final String SELECT = """
            SELECT id, spent_on, category, description, amount_rupees, mode, paid_to, created_by, created_at
            FROM expense
            """;

    private final JdbcClient jdbc;

    ExpenseService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Expenses between two dates (inclusive), optionally of one category, newest first. */
    @Transactional(readOnly = true)
    public ExpenseList list(LocalDate from, LocalDate to, @Nullable ExpenseCategory category) {
        var items = jdbc.sql(SELECT + """
                        WHERE spent_on BETWEEN :from AND :to
                          AND (CAST(:category AS varchar) IS NULL OR category = :category)
                        ORDER BY spent_on DESC, id DESC
                        """)
                .param("from", from)
                .param("to", to)
                .param("category", category == null ? null : category.name())
                .query(Expense.class)
                .list();
        var byCategory = items.stream()
                .collect(Collectors.groupingBy(
                        Expense::category, Collectors.reducing(Rupees.ZERO, Expense::amountRupees, BigDecimal::add)))
                .entrySet()
                .stream()
                .map(entry -> new CategoryTotal(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryTotal::amountRupees).reversed())
                .toList();
        return new ExpenseList(items, Rupees.sum(items, Expense::amountRupees), byCategory);
    }

    @Transactional(readOnly = true)
    public Expense get(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param(ID, id)
                .query(Expense.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    @Transactional
    public Expense create(ExpenseForm form, @Nullable String actor) {
        long id = jdbc.sql("""
                        INSERT INTO expense (spent_on, category, description, amount_rupees, mode, paid_to, created_by)
                        VALUES (:spentOn, :category, :description, :amount, :mode, :paidTo, :by) RETURNING id
                        """)
                .param("spentOn", form.spentOn())
                .param("category", form.category().name())
                .param("description", form.description().trim())
                .param("amount", form.amountRupees())
                .param("mode", form.mode().name())
                .param("paidTo", blankToNull(form.paidTo()))
                .param("by", actor)
                .query(Long.class)
                .single();
        return get(id);
    }

    @Transactional
    public Expense update(long id, ExpenseForm form) {
        int updated = jdbc.sql("""
                        UPDATE expense SET spent_on = :spentOn, category = :category, description = :description,
                            amount_rupees = :amount, mode = :mode, paid_to = :paidTo
                        WHERE id = :id
                        """)
                .param("spentOn", form.spentOn())
                .param("category", form.category().name())
                .param("description", form.description().trim())
                .param("amount", form.amountRupees())
                .param("mode", form.mode().name())
                .param("paidTo", blankToNull(form.paidTo()))
                .param(ID, id)
                .update();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        }
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.sql("DELETE FROM expense WHERE id = :id").param(ID, id).update() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        }
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
