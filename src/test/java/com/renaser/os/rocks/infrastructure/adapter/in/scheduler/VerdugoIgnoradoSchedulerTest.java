package com.renaser.os.rocks.infrastructure.adapter.in.scheduler;

import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-562. Caracteriza lo que hace HOY el barrido a las 23:55 UTC (= 18:55 en Lima): pide resolver los eventos
 * pendientes del dia UTC en curso. No hay regla de negocio sobre "el dia" del Verdugo (ver E-562): esta prueba fija
 * el comportamiento, no lo aprueba.
 */
class VerdugoIgnoradoSchedulerTest {

    @Test
    void a23_55UtcResuelveLosPendientesDelDiaUtcEnCurso() {
        List<LocalDate> pedidos = new ArrayList<>();
        var scheduler = new VerdugoIgnoradoScheduler(pedidos::add,
                FixedClock.at(Instant.parse("2026-10-06T23:55:00Z")));

        scheduler.resolverPendientesDeHoy();

        assertThat(pedidos).containsExactly(LocalDate.of(2026, 10, 6));
    }
}
