package com.vaabhi.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class IndianStatesTest {

    @Test
    void looksUpGstCodes() {
        assertThat(IndianStates.code("Telangana")).contains("36");
        assertThat(IndianStates.code("Karnataka")).contains("29");
        assertThat(IndianStates.code("Dadra and Nagar Haveli and Daman and Diu"))
                .contains("26");
        assertThat(IndianStates.code("Atlantis")).isEmpty();
    }

    @Test
    void listsEveryStateAndTerritoryOnce() {
        var all = IndianStates.all();
        assertThat(all).hasSize(36);
        assertThat(new HashSet<>(all.stream().map(IndianStates.State::name).toList()))
                .hasSize(all.size());
        assertThat(all).allSatisfy(s -> assertThat(s.code()).matches("^[0-9]{2}$"));
    }
}
