package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.settings.BillingSettings;
import com.vaabhi.settings.ShopProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The seller, heading and date an invoice freezes at issue. */
class InvoicePartiesTest {

    private static ShopProfile profile(String address, String state) {
        return new ShopProfile(
                "Owner",
                "vaabhi",
                ShopProfile.BusinessType.LADIES_BOUTIQUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                address,
                null,
                null,
                null,
                null,
                null,
                state,
                "IN",
                null,
                "INR",
                LocalTime.of(9, 0),
                LocalTime.of(21, 0),
                null);
    }

    private static BillingSettings billing(boolean chargeGst, String gstin, BillingSettings.Company company) {
        return new BillingSettings(
                chargeGst,
                gstin,
                new BigDecimal("5"),
                true,
                false,
                BillingSettings.DiscountBase.ORDER_TOTAL,
                true,
                company,
                null);
    }

    private static final BillingSettings.Company NO_COMPANY = new BillingSettings.Company(null, null, null, null);

    @Test
    void titleIsPlainInvoiceWithoutGst() {
        assertThat(InvoiceParties.title(billing(false, null, NO_COMPANY))).isEqualTo("Invoice");
    }

    @Test
    void titleIsTaxInvoiceWithGstAndGstin() {
        assertThat(InvoiceParties.title(billing(true, "36ABCDE1234F1Z5", NO_COMPANY)))
                .isEqualTo("Tax Invoice");
    }

    @Test
    void sellerIsTheCompanyWhenFilledIn() {
        var company = new BillingSettings.Company(
                "Vaabhi Boutique Private Limited", "U74999TG2026OPC123456", "1-2-3 Road, Hyderabad", "Telangana");

        var seller = InvoiceParties.seller(profile("Shop address", "Karnataka"), billing(false, null, company));

        assertThat(seller.name()).isEqualTo("Vaabhi Boutique Private Limited");
        assertThat(seller.address()).isEqualTo("1-2-3 Road, Hyderabad");
        assertThat(seller.cin()).isEqualTo("U74999TG2026OPC123456");
        assertThat(seller.state()).isEqualTo("Telangana");
        assertThat(seller.stateCode()).isEqualTo("36");
    }

    @Test
    void sellerFallsBackToTheShop() {
        var seller = InvoiceParties.seller(profile("Shop address", "Telangana"), billing(false, null, NO_COMPANY));

        assertThat(seller.name()).isEqualTo("vaabhi");
        assertThat(seller.address()).isEqualTo("Shop address");
        assertThat(seller.cin()).isNull();
        assertThat(seller.stateCode()).isEqualTo("36");
    }

    @Test
    void invoiceDateIsTheIndianDay() {
        var clock = Clock.fixed(Instant.parse("2027-03-31T19:00:00Z"), ZoneOffset.UTC);

        assertThat(InvoiceParties.today(clock)).isEqualTo(LocalDate.of(2027, 4, 1));
    }
}
