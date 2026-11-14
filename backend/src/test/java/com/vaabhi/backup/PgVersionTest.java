package com.vaabhi.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PgVersionTest {

    @Test
    void clientVersionTakesTheMajor() {
        assertThat(PgVersion.ofClient("pg_dump (PostgreSQL) 17.2").major()).isEqualTo(17);
        assertThat(PgVersion.ofClient("pg_dump (PostgreSQL) 16.4 (Debian 16.4-1)")
                        .major())
                .isEqualTo(16);
    }

    @Test
    void serverNumTakesTheMajor() {
        assertThat(PgVersion.ofServerNum(170002).major()).isEqualTo(17);
        assertThat(PgVersion.ofServerNum(160004).major()).isEqualTo(16);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> PgVersion.ofClient("pg_dump: command not found"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PgVersion.ofServerNum(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
