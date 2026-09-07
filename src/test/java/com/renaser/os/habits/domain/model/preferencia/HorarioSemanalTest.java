package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Los lunes a las 5 y los martes a las 4" (V39) — las invariantes del agregado. */
class HorarioSemanalTest {

    private static final Instant AHORA = Instant.parse("2026-09-07T15:00:00Z");
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());

    private PreferenciaHorario preferencia(LocalTime disparo, LocalTime limite) {
        return PreferenciaHorario.crear(actor, habito, disparo, limite, AHORA);
    }

    @Test
    void guardaElDiaYLaHoraDeEseDia() {
        var lunes = new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null));

        assertThat(lunes.diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(lunes.preferencia().horaDisparo()).isEqualTo(LocalTime.of(5, 0));
    }

    /**
     * Sin hora de disparo esta fila no dice NADA que la preferencia general no diga ya. Dejarla
     * entrar convertiria "volver al horario general" en dos operaciones distintas —borrar la fila
     * o vaciarla— que se comportarian igual pero se leerian distinto en la base.
     */
    @Test
    void sinHoraDeDisparoNoSePuedeConstruir() {
        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.MONDAY, preferencia(null, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("horaDisparo");
    }

    @Test
    void laHoraLimiteTieneQueSerPosteriorALaDeDisparo() {
        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.TUESDAY,
                preferencia(LocalTime.of(7, 0), LocalTime.of(6, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaLimite");
    }

    /** La mayoria de los habitos no vence dentro del dia: la hora limite es opcional. */
    @Test
    void sinHoraLimiteEsValido() {
        var noche = new HorarioSemanal(DayOfWeek.SUNDAY, preferencia(LocalTime.of(22, 0), null));

        assertThat(noche.preferencia().horaLimite()).isNull();
    }

    @Test
    void elDiaEsObligatorio() {
        assertThatThrownBy(() -> new HorarioSemanal(null, preferencia(LocalTime.of(5, 0), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("diaSemana");
    }
}
