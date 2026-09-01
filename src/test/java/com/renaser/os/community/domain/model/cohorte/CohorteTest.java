package com.renaser.os.community.domain.model.cohorte;

import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** community/service.ts:69-72 (isValidTransition): solo hacia adelante y de a un paso. */
class CohorteTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final CohorteId ID = CohorteId.of(UUID.randomUUID());

    private static Cohorte nueva() {
        return Cohorte.crear(ID, "Cohorte Agosto", LocalDate.of(2026, 8, 1), null, CLOCK.now());
    }

    @Test
    void crearNaceEnPlanificada() {
        Cohorte c = nueva();
        assertThat(c.id()).isEqualTo(ID);
        assertThat(c.estado()).isEqualTo(EstadoCohorte.PLANIFICADA);
    }

    @Test
    void nombreVacioEsInvalido() {
        assertThatThrownBy(() -> Cohorte.crear(ID, "  ", LocalDate.now(), null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fechaFinAnteriorAFechaInicioEsInvalida() {
        assertThatThrownBy(() -> Cohorte.crear(ID, "X", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1),
                CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transicionPlanificadaAActivaEsValida() {
        Cohorte c = nueva();
        c.transicionarA(EstadoCohorte.ACTIVA, CLOCK.now());
        assertThat(c.estado()).isEqualTo(EstadoCohorte.ACTIVA);
    }

    @Test
    void transicionSaltandoUnPasoEsInvalida() {
        Cohorte c = nueva();
        assertThatThrownBy(() -> c.transicionarA(EstadoCohorte.COMPLETADA, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transicionHaciaAtrasEsInvalida() {
        Cohorte c = nueva();
        c.transicionarA(EstadoCohorte.ACTIVA, CLOCK.now());
        assertThatThrownBy(() -> c.transicionarA(EstadoCohorte.PLANIFICADA, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
