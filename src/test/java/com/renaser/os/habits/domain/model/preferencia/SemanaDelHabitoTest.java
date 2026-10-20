package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito.Dia;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 008: los siete dias ya resueltos. La hora y el aviso se resuelven por separado: cada dia usa lo
 * propio de cada campo y, si no lo trae, lo general.
 */
class SemanaDelHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T10:00:00Z");
    private static final LocalTime SIETE = LocalTime.of(7, 0);
    private static final RecordatorioDelDia GENERAL = RecordatorioDelDia.con(List.of(10));
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());

    private HorarioSemanal dia(DayOfWeek dia, LocalTime hora, boolean activo, RecordatorioDelDia aviso) {
        return new HorarioSemanal(dia, PreferenciaHorario.crear(actor, habito, hora, null, AHORA), activo, aviso);
    }

    private static Dia del(List<Dia> semana, DayOfWeek dia) {
        return semana.stream().filter(d -> d.diaSemana() == dia).findFirst().orElseThrow();
    }

    @Test
    void siempreDevuelveLosSieteDiasEnOrdenDeLaSemana() {
        var semana = SemanaDelHabito.resolver(List.of(), SIETE, null, GENERAL);

        assertThat(semana).extracting(Dia::diaSemana).containsExactly(DayOfWeek.values());
    }

    @Test
    void unDiaSinFilaHeredaLaHoraYElAvisoGenerales() {
        var lunes = del(SemanaDelHabito.resolver(List.of(), SIETE, null, GENERAL), DayOfWeek.MONDAY);

        assertThat(lunes.horaDisparo()).isEqualTo(SIETE);
        assertThat(lunes.horaPropia()).isFalse();
        assertThat(lunes.recordatorio()).isEqualTo(GENERAL);
        assertThat(lunes.recordatorioPropio()).isFalse();
        assertThat(lunes.activo()).isTrue();
    }

    @Test
    void unDiaConHoraYAvisoPropiosLosTraeLosDos() {
        var semana = SemanaDelHabito.resolver(
                List.of(dia(DayOfWeek.SATURDAY, LocalTime.of(9, 0), true, RecordatorioDelDia.con(List.of(30)))),
                SIETE, null, GENERAL);

        var sabado = del(semana, DayOfWeek.SATURDAY);
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.horaPropia()).isTrue();
        assertThat(sabado.recordatorio()).isEqualTo(RecordatorioDelDia.con(List.of(30)));
        assertThat(sabado.recordatorioPropio()).isTrue();
    }

    @Test
    void unaFilaDeSoloAvisoDejaLaHoraGeneralYNoCuentaComoHoraPropia() {
        var semana = SemanaDelHabito.resolver(
                List.of(dia(DayOfWeek.SATURDAY, null, true, RecordatorioDelDia.con(List.of(30)))),
                SIETE, null, GENERAL);

        var sabado = del(semana, DayOfWeek.SATURDAY);
        assertThat(sabado.horaDisparo()).isEqualTo(SIETE);
        assertThat(sabado.horaPropia()).as("no hay hora propia: sigue a la general").isFalse();
        assertThat(sabado.recordatorioPropio()).isTrue();
    }

    @Test
    void unaFilaDeSoloHoraHeredaElAvisoGeneral() {
        var semana = SemanaDelHabito.resolver(List.of(dia(DayOfWeek.SATURDAY, LocalTime.of(9, 0), true, null)),
                SIETE, null, GENERAL);

        var sabado = del(semana, DayOfWeek.SATURDAY);
        assertThat(sabado.recordatorio()).isEqualTo(GENERAL);
        assertThat(sabado.recordatorioPropio()).isFalse();
        assertThat(sabado.horaPropia()).isTrue();
    }

    @Test
    void sinAvisoPropioEsUnAvisoPropioApagado() {
        var semana = SemanaDelHabito.resolver(
                List.of(dia(DayOfWeek.SATURDAY, null, true, RecordatorioDelDia.sinAviso())), SIETE, null, GENERAL);

        var sabado = del(semana, DayOfWeek.SATURDAY);
        assertThat(sabado.recordatorio().activo()).isFalse();
        assertThat(sabado.recordatorioPropio()).isTrue();
    }

    /** Se conserva lo de V40: un dia apagado cuenta como propio y muestra la hora general. */
    @Test
    void unDiaApagadoSigueSiendoPropioYMuestraLaHoraGeneral() {
        var semana = SemanaDelHabito.resolver(List.of(dia(DayOfWeek.TUESDAY, null, false, null)),
                SIETE, null, GENERAL);

        var martes = del(semana, DayOfWeek.TUESDAY);
        assertThat(martes.activo()).isFalse();
        assertThat(martes.horaPropia()).isTrue();
        assertThat(martes.horaDisparo()).isEqualTo(SIETE);
    }

    @Test
    void laHoraLimiteSigueElRespaldoPorCampo() {
        var semana = SemanaDelHabito.resolver(List.of(dia(DayOfWeek.MONDAY, LocalTime.of(9, 0), true, null)),
                SIETE, LocalTime.of(12, 0), GENERAL);

        assertThat(del(semana, DayOfWeek.MONDAY).horaLimite()).isEqualTo(LocalTime.of(12, 0));
        assertThat(del(semana, DayOfWeek.TUESDAY).horaLimite()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void sinHoraGeneralNiPropiaLaHoraQuedaVacia() {
        var semana = SemanaDelHabito.resolver(List.of(), null, null, RecordatorioDelDia.sinAviso());

        assertThat(del(semana, DayOfWeek.MONDAY).horaDisparo()).isNull();
        assertThat(del(semana, DayOfWeek.MONDAY).recordatorio().activo()).isFalse();
    }
}
