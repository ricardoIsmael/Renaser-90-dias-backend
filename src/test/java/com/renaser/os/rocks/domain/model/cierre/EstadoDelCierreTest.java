package com.renaser.os.rocks.domain.model.cierre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoDelCierreTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

    @Test
    @DisplayName("hoy sin marca esta abierto; hoy con marca, cerrado")
    void hoy() {
        assertThat(EstadoDelCierre.de(HOY, HOY, false)).isEqualTo(EstadoDelCierre.ABIERTO);
        assertThat(EstadoDelCierre.de(HOY, HOY, true)).isEqualTo(EstadoDelCierre.CERRADO);
    }

    @Test
    @DisplayName("un dia que ya termino sin marca dice «sin cerrar»; con marca, cerrado")
    void diaAnterior() {
        assertThat(EstadoDelCierre.de(HOY.minusDays(1), HOY, false)).isEqualTo(EstadoDelCierre.SIN_CERRAR);
        assertThat(EstadoDelCierre.de(HOY.minusDays(1), HOY, true)).isEqualTo(EstadoDelCierre.CERRADO);
    }
}
