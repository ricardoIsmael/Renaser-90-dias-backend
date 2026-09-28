package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-217 (2026-09-28): el recordatorio guarda TODAS las antelaciones («30 min antes y a la hora»), y
 * {@code minutos_recordatorio} sigue siendo la más temprana para el APK de producción. Contra el código
 * anterior no compila: la preferencia solo tenía un número, y un teléfono nuevo volvía con un aviso
 * (E-398).
 */
class AntelacionesDelRecordatorioTest {

    // 02:00 UTC = 21:00 del dia anterior en Lima (regla 02): nada de esto depende de la hora.
    private static final Instant AHORA = Instant.parse("2026-09-29T02:00:00Z");

    private static PreferenciaHorario preferencia() {
        return PreferenciaHorario.crear(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                LocalTime.of(10, 0), null, AHORA);
    }

    @Test
    @DisplayName("con el conjunto: se guarda sin repetidos, de la más temprana a la más tardía, y los minutos son la primera")
    void guardaElConjuntoYLosMinutosSonLaMasTemprana() {
        PreferenciaHorario p = preferencia();
        p.actualizarRecordatorioConAntelaciones(true, List.of(0, 30, 0), AHORA);
        assertThat(p.antelacionesRecordatorio()).containsExactly(30, 0);
        assertThat(p.minutosRecordatorio()).isEqualTo(30);
    }

    @Test
    @DisplayName("apagado o vacío: sin conjunto")
    void apagadoSinConjunto() {
        PreferenciaHorario p = preferencia();
        p.actualizarRecordatorioConAntelaciones(false, List.of(30, 0), AHORA);
        assertThat(p.antelacionesRecordatorio()).isNull();
        p.actualizarRecordatorioConAntelaciones(true, List.of(), AHORA);
        assertThat(p.antelacionesRecordatorio()).isNull();
        assertThat(p.minutosRecordatorio()).isNull();
    }

    @Test
    @DisplayName("un valor fuera de rango o vacío se rechaza, con el mismo rango de siempre")
    void rechazaValoresInvalidos() {
        assertThatThrownBy(() -> preferencia().actualizarRecordatorioConAntelaciones(true, List.of(30, -5), AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0 y 32767");
        assertThatThrownBy(() -> preferencia().actualizarRecordatorioConAntelaciones(true, Arrays.asList(30, null), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("solo el número (APK viejo, acompañante): si es la más temprana, el conjunto se conserva")
    void elNumeroQueCoincideConservaElConjunto() {
        PreferenciaHorario p = preferencia();
        p.actualizarRecordatorioConAntelaciones(true, List.of(30, 0), AHORA);
        p.actualizarRecordatorio(true, 30, AHORA);
        assertThat(p.antelacionesRecordatorio()).containsExactly(30, 0);
    }

    @Test
    @DisplayName("solo el número y distinto: el conjunto pasa a ser ese número")
    void elNumeroDistintoReemplazaElConjunto() {
        PreferenciaHorario p = preferencia();
        p.actualizarRecordatorioConAntelaciones(true, List.of(30, 0), AHORA);
        p.actualizarRecordatorio(true, 10, AHORA);
        assertThat(p.antelacionesRecordatorio()).containsExactly(10);
        assertThat(p.minutosRecordatorio()).isEqualTo(10);
    }

    @Test
    @DisplayName("solo el número con el recordatorio apagado: sin conjunto")
    void elNumeroApagadoBorraElConjunto() {
        PreferenciaHorario p = preferencia();
        p.actualizarRecordatorioConAntelaciones(true, List.of(30, 0), AHORA);
        p.actualizarRecordatorio(false, 30, AHORA);
        assertThat(p.antelacionesRecordatorio()).isNull();
    }

    @Test
    @DisplayName("fila anterior a V81 (sin conjunto): el mismo número sigue sin conjunto; uno nuevo lo fija")
    void filaViejaSigueDesconocidaSiNoCambia() {
        PreferenciaHorario vieja = PreferenciaHorario.rehydrate(UserId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalTime.of(10, 0), null, true, 30, AHORA, AHORA);
        vieja.actualizarRecordatorio(true, 30, AHORA);
        assertThat(vieja.antelacionesRecordatorio()).isNull();
        vieja.actualizarRecordatorio(true, 15, AHORA);
        assertThat(vieja.antelacionesRecordatorio()).containsExactly(15);
    }
}
