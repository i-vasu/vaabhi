package com.vaabhi.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vaabhi.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Razorpay settlement sync: processed batches are stored once, and nothing is called when the keys are unset. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class RazorpaySettlementSyncIT {

    @Autowired
    private RazorpaySettlementSync sync;

    @MockitoBean
    private RazorpayClient client;

    private static RazorpaySettlementData settlement(String status) {
        return new RazorpaySettlementData(
                "setl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
                null,
                1_000_000,
                23_600,
                3_600,
                status,
                Instant.now().getEpochSecond());
    }

    @Test
    void storesProcessedSettlementsOnce() {
        given(client.configured()).willReturn(true);
        given(client.settlements(anyLong(), anyLong(), anyInt()))
                .willReturn(List.of(settlement("processed"), settlement("processed"), settlement("created")));

        assertThat(sync.syncNow().added()).isEqualTo(2);
        assertThat(sync.syncNow().added()).isZero();
    }

    @Test
    void notConfiguredDoesNothing() {
        given(client.configured()).willReturn(false);

        var outcome = sync.syncNow();

        assertThat(outcome).isEqualTo(new RazorpaySyncOutcome(false, 0, null));
        verify(client, never()).settlements(anyLong(), anyLong(), anyInt());
    }
}
