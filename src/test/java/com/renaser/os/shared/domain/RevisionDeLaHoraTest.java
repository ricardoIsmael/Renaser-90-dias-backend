package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cómo se lee la hora real de lo hecho sin red (spec 048, fase 3, D13 y D14). Sin Spring ni reloj: {@code ahora} entra por parámetro.
 * Los instantes caen en la madrugada UTC (el día local de Lima es el anterior), regla 02.
 */
class RevisionDeLaHoraTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 21:40 del 9 de noviembre en Lima = 02:40 del 10 en UTC. */
    private static final Instant ANOCHE = Instant.parse("2026-11-10T02:40:00Z");
    /** 08:00 del 10 en Lima = 13:00 UTC. */
    private static final Instant ESTA_MANANA = Instant.parse("2026-11-10T13:00:00Z");

    @Test
    @DisplayName("la referencia es la hora real cuando ya pasó: el «hoy» de la hora real es el de anoche en Lima, no el de la llegada")
    void referenciaEsLaHoraRealYDefineSuDia() { // spec 048 FR-019
        RevisionDeLaHora hora = RevisionDeLaHora.de(ANOCHE, ESTA_MANANA);

        assertThat(hora.referencia()).isEqualTo(ANOCHE);
        assertThat(hora.hoyEn(LIMA)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(hora.hoyDeLaLlegadaEn(LIMA)).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("una hora del futuro no adelanta nada: la referencia es ahora, y pasada la tolerancia de 2 minutos es hora futura")
    void horaFuturaUsaAhora() { // spec 048 FR-019
        Instant adelantada = ESTA_MANANA.plusSeconds(119);
        Instant muyAdelantada = ESTA_MANANA.plusSeconds(121);

        assertThat(RevisionDeLaHora.de(adelantada, ESTA_MANANA).referencia()).isEqualTo(ESTA_MANANA);
        assertThat(RevisionDeLaHora.de(adelantada, ESTA_MANANA).esHoraFutura()).isFalse();
        assertThat(RevisionDeLaHora.de(muyAdelantada, ESTA_MANANA).referencia()).isEqualTo(ESTA_MANANA);
        assertThat(RevisionDeLaHora.de(muyAdelantada, ESTA_MANANA).esHoraFutura()).isTrue();
    }

    @Test
    @DisplayName("llegar a las 48 h justas todavía cuenta; un segundo más, no (solo quita puntos)")
    void plazoDeLlegada() { // spec 048 FR-019
        Instant hecho = Instant.parse("2026-11-08T13:00:00Z");

        assertThat(RevisionDeLaHora.de(hecho, hecho.plusSeconds(48 * 3600)).llegoTarde()).isFalse();
        assertThat(RevisionDeLaHora.de(hecho, hecho.plusSeconds(48 * 3600 + 1)).llegoTarde()).isTrue();
    }

    @Test
    @DisplayName("el día del programa a la hora real es el de hoy menos los días entre esa hora y la llegada, con piso 0")
    void diaDelProgramaALaHoraReal() { // spec 048 FR-019
        // Anoche (día 9 en Lima) y se llega hoy (día 10): hoy es el día 8 del programa, anoche era el 7.
        assertThat(RevisionDeLaHora.de(ANOCHE, ESTA_MANANA).diaDelPrograma(8, LIMA)).isEqualTo(7);
        // Mismo día local: no cambia.
        assertThat(RevisionDeLaHora.de(ESTA_MANANA.minusSeconds(3600), ESTA_MANANA).diaDelPrograma(8, LIMA)).isEqualTo(8);
        // Hecho hace tres días cuando hoy es el día 2: no baja de 0.
        assertThat(RevisionDeLaHora.de(ESTA_MANANA.minusSeconds(3 * 86400), ESTA_MANANA).diaDelPrograma(2, LIMA)).isZero();
    }

    @Test
    @DisplayName("D13: una fila cambiada DESPUÉS de lo que hiciste choca; una anterior, igual o sin hora, no")
    void elCambioMasNuevoGana() { // spec 048 FR-019
        RevisionDeLaHora hora = RevisionDeLaHora.de(ANOCHE, ESTA_MANANA);

        assertThat(hora.hayCambioMasNuevo(ANOCHE.plusMillis(1))).isTrue();
        assertThat(hora.hayCambioMasNuevo(ANOCHE)).isFalse();
        assertThat(hora.hayCambioMasNuevo(ANOCHE.minusSeconds(60))).isFalse();
        assertThat(hora.hayCambioMasNuevo(null)).isFalse();
    }

    @Test
    @DisplayName("el origen de la hora desconocido o vacío se lee como el más desconfiable")
    void origenDeLaHora() { // spec 048 FR-013
        assertThat(OrigenDeLaHora.leer("RELOJ_DEL_SISTEMA")).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
        assertThat(OrigenDeLaHora.leer("TELEFONO_CORREGIDO")).isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
        assertThat(OrigenDeLaHora.leer("otra")).isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
        assertThat(OrigenDeLaHora.leer(null)).isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
        assertThat(OrigenDeLaHora.leer("  ")).isEqualTo(OrigenDeLaHora.TELEFONO_CORREGIDO);
    }
}
