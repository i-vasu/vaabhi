package com.vaabhi.money;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * One month of GSTR-1: the sections of the offline-tool workbook. Row columns are keyed by the offline tool's column
 * names, in its order.
 */
public record Gstr1(
        YearMonth month,
        boolean gstOn,
        List<Gstr1Row> b2b,
        List<Gstr1Row> b2cl,
        List<Gstr1Row> b2cs,
        List<Gstr1Row> cdnr,
        List<Gstr1Row> cdnur,
        List<Gstr1HsnRow> hsn,
        List<Gstr1DocRange> docs) {

    /** A row of B2B, B2CL, B2CS, CDNR or CDNUR, with the totals the page adds up. */
    public record Gstr1Row(
            Map<String, Object> columns,
            BigDecimal taxable,
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal value) {}

    /** HSN summary row, net of credit notes. */
    public record Gstr1HsnRow(
            String hsn,
            String description,
            String uqc,
            long quantity,
            BigDecimal value,
            BigDecimal rate,
            BigDecimal taxable,
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal cess) {}

    /** Documents issued in the month: the first and last number, how many, how many cancelled. */
    public record Gstr1DocRange(String nature, String from, String to, int total, int cancelled) {}
}
