package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cambiar la duración del programa de quien ya empezó (spec 025, 025-2 A): conserva el día, mueve el último y recalcula la fase. */
class ParticipacionProgramaCambioDeDuracionTest {

    /** 10:00 en Lima. */
    private static final Instant INICIO = Instant.parse("2026-01-10T15:00:00Z");

    private static ParticipacionPrograma enElDia(int dia, Formacion formacion, int duracion) {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), FixedClock.at(INICIO), formacion, duracion);
        FixedClock reloj = reloj(dia);
        p.sincronizarDiaDelPrograma(reloj.now().atZone(p.timezone()).toLocalDate(), reloj);
        return p;
    }

    private static FixedClock reloj(int dia) {
        return FixedClock.at(INICIO.plus(dia - 1L, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("de 3 meses a 1 año en el día 40: sigue en el día 40, su último día pasa a ser el 365 y la fase se estira (la 3 del 90 pasa a la 2 del año)")
    void deTresMesesAUnAnio() {
        ParticipacionPrograma p = enElDia(40, Formacion.DIPLOMADO, 90);
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);

        p.cambiarDuracion(365, reloj(40));

        assertThat(p.duracionDias()).isEqualTo(365);
        assertThat(p.diaVigente(reloj(40))).as("conserva su día").isEqualTo(40);
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
        assertThat(p.programaCompletado()).isFalse();
    }

    @Test
    @DisplayName("de 1 año a 3 meses, mientras su día quepa: en el día 40 sí")
    void deUnAnioATresMeses() {
        ParticipacionPrograma p = enElDia(40, Formacion.DIPLOMADO, 365);

        p.cambiarDuracion(90, reloj(40));

        assertThat(p.duracionDias()).isEqualTo(90);
        assertThat(p.diaVigente(reloj(40))).isEqualTo(40);
    }

    @Test
    @DisplayName("no se baja la duración hasta o por debajo del día en que va: se graduaría en el acto")
    void noSeBajaPorDebajoDelDia() {
        ParticipacionPrograma p = enElDia(200, Formacion.DIPLOMADO, 365);

        assertThatThrownBy(() -> p.cambiarDuracion(90, reloj(200))).isInstanceOf(IllegalStateException.class).hasMessageContaining("día 200");
        assertThatThrownBy(() -> enElDia(90, Formacion.DIPLOMADO, 365).cambiarDuracion(90, reloj(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(p.duracionDias()).isEqualTo(365);
    }

    @Test
    @DisplayName("no se cambia lo que ya terminó, ni se pone la misma duración, ni una fuera de rango")
    void rechazaLoQueNoCorresponde() {
        ParticipacionPrograma terminada = enElDia(90, Formacion.ESCUELA, 90);
        assertThat(terminada.programaCompletado()).isTrue();
        assertThatThrownBy(() -> terminada.cambiarDuracion(365, reloj(90))).isInstanceOf(IllegalStateException.class).hasMessageContaining("terminó");

        ParticipacionPrograma p = enElDia(10, Formacion.DIPLOMADO, 90);
        assertThatThrownBy(() -> p.cambiarDuracion(90, reloj(10))).isInstanceOf(IllegalStateException.class).hasMessageContaining("ya tiene");
        assertThatThrownBy(() -> p.cambiarDuracion(400, reloj(10))).isInstanceOf(IllegalArgumentException.class);
    }
}
