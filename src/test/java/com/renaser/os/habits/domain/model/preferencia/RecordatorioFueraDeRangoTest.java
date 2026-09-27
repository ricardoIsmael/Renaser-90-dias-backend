package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PLN-09 (e2e del 2026-09-27): {@code minutos_recordatorio} es {@code smallint} con
 * {@code CHECK (minutos_recordatorio >= 0)} en {@code preferencias_horario} y en
 * {@code horarios_habito_por_fecha}. El dominio no lo validaba: un -5 lo frenaba el CHECK y un 99999
 * no entra en un {@code smallint} (el mapper lo convierte con {@code shortValue()} y queda negativo).
 * Los dos volvian como 409 «La operacion entra en conflicto con datos que ya existen».
 */
class RecordatorioFueraDeRangoTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T19:18:16Z");
    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());

    private static PreferenciaHorario preferencia() {
        return PreferenciaHorario.crear(PERSONA, HABITO, LocalTime.of(11, 0), null, AHORA);
    }

    @ParameterizedTest(name = "{0} minutos")
    @ValueSource(ints = {-5, -1, 32_768, 99_999})
    @DisplayName("PLN-09: minutos fuera de lo que guarda la base -> se rechaza con el rango, sin tocar la preferencia")
    void rechazaMinutosFueraDeLoQueGuardaLaBase(int minutos) {
        PreferenciaHorario preferencia = preferencia();

        assertThatThrownBy(() -> preferencia.actualizarRecordatorio(true, minutos, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 0 y 32767");
        assertThat(preferencia.minutosRecordatorio()).isNull();
    }

    @ParameterizedTest(name = "{0} minutos")
    @ValueSource(ints = {0, 10, 1_440, 32_767})
    @DisplayName("PLN-09: el rango de la base entero sigue valiendo, incluido 0 (\"a la hora\")")
    void aceptaElRangoDeLaBase(int minutos) {
        PreferenciaHorario preferencia = preferencia();

        preferencia.actualizarRecordatorio(true, minutos, AHORA);

        assertThat(preferencia.minutosRecordatorio()).isEqualTo(minutos);
    }

    @Test
    @DisplayName("PLN-09: sin minutos elegidos sigue valiendo (recordatorio apagado o \"a la hora\" del telefono)")
    void sinMinutosSigueValiendo() {
        PreferenciaHorario preferencia = preferencia();

        preferencia.actualizarRecordatorio(false, null, AHORA);

        assertThat(preferencia.minutosRecordatorio()).isNull();
    }

    @Test
    @DisplayName("PLN-09: un cambio programado para mañana tampoco guarda minutos fuera de rango")
    void unCambioProgramadoTambienRechaza() {
        assertThatThrownBy(() -> CambioHorarioPendiente.programar(PERSONA, HABITO, LocalTime.of(11, 45), null,
                true, -5, LocalDate.of(2026, 9, 28), AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 0 y 32767");
    }
}
