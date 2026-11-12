package com.vaabhi.money;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Staff issuing a credit note on an invoice; no lines means the whole invoice. */
public record CreditNoteForm(
        @NotNull CreditNote.Reason reason,
        @Nullable @Size(max = 200) String note,
        List<@Valid Line> lines) {

    public record Line(long invoiceLineId, @Min(1) int quantity) {}
}
