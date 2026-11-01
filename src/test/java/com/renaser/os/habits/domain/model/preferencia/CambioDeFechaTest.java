package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CambioDeFechaTest {

    private final HabitoId habito = HabitoId.of(UUID.randomUUID());
    private final LocalDate fecha = LocalDate.of(2026, 11, 7);
    /** El sabado del plan de siempre: 07:00, aviso 10, como suena lo de hoy. */
    private final SemanaDelHabito.Dia base = new SemanaDelHabito.Dia(DayOfWeek.SATURDAY, LocalTime.of(7, 0), null, false, true,
            RecordatorioDelDia.con(List.of(10)), false, CombinacionDeAlarma.COMO_HOY, false);

    private CambioDeFecha cambio(Boolean activo, LocalTime hora, RecordatorioDelDia aviso, CombinacionDeAlarma alarma) {
        return new CambioDeFecha(fecha, false, activo, hora, null, aviso, alarma);
    }

    @Test
    void moverLaHoraGuardaSoloLaHoraYDejaHeredarElRestoDelDia() {
        FechaPropia fila = cambio(null, LocalTime.of(9, 0), null, null).aplicarA(habito, null, base, true).orElseThrow();

        assertThat(fila.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fila.recordatorio()).isNull();
        assertThat(fila.alarma()).isNull();
        assertThat(fila.activo()).isTrue();
    }

    @Test
    void cambiarSoloElAvisoNoCongelaLaHora() {
        FechaPropia fila = cambio(null, null, RecordatorioDelDia.con(List.of(30, 0)), null).aplicarA(habito, null, base, true)
                .orElseThrow();

        assertThat(fila.horaDisparo()).isNull();
        assertThat(fila.recordatorio().antelaciones()).containsExactly(30, 0);
    }

    @Test
    void unaHoraIgualALaDeSiempreSeGuardaComoHereda() {
        assertThat(cambio(null, LocalTime.of(7, 0), null, null).aplicarA(habito, null, base, true)).isEmpty();
    }

    @Test
    void unAvisoIgualAlDeSiempreSeGuardaComoHeredaYLaFilaSeBorraSiNoQuedaNada() {
        assertThat(cambio(null, null, RecordatorioDelDia.con(List.of(10)), null).aplicarA(habito, null, base, true)).isEmpty();
    }

    @Test
    void unaAlarmaIgualALaDeSiempreNoSeGuarda() {
        assertThat(cambio(null, null, null, CombinacionDeAlarma.COMO_HOY).aplicarA(habito, null, base, true)).isEmpty();
    }

    @Test
    void unaAlarmaDistintaSeGuardaEntera() {
        var soloVibrar = CombinacionDeAlarma.de(false, true, false, false);

        FechaPropia fila = cambio(null, null, null, soloVibrar).aplicarA(habito, null, base, true).orElseThrow();

        assertThat(fila.alarma()).isEqualTo(AjusteDeAlarma.de(soloVibrar));
    }

    @Test
    void lasCosasNoTocadasSeConservanDeLaFilaQueYaEstaba() {
        var existente = new FechaPropia(habito, fecha, true, LocalTime.of(9, 0), null, null, null);

        FechaPropia fila = cambio(null, null, RecordatorioDelDia.sinAviso(), null).aplicarA(habito, existente, base, true).orElseThrow();

        assertThat(fila.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fila.recordatorio()).isEqualTo(RecordatorioDelDia.sinAviso());
    }

    @Test
    void devolverLaHoraALaDeSiempreBorraLaFilaSiNoQuedaNadaPropio() {
        var existente = new FechaPropia(habito, fecha, true, LocalTime.of(9, 0), null, null, null);

        assertThat(cambio(null, LocalTime.of(7, 0), null, null).aplicarA(habito, existente, base, true)).isEmpty();
    }

    @Test
    void apagarElDiaGuardaUnaFilaApagada() {
        FechaPropia fila = cambio(false, null, null, null).aplicarA(habito, null, base, true).orElseThrow();

        assertThat(fila.activo()).isFalse();
    }

    @Test
    void unHabitoObligatorioNoSePuedeApagar() {
        assertThatThrownBy(() -> cambio(false, null, null, null).aplicarA(habito, null, base, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encenderUnDiaApagadoEnElPlanDeSiempreSeRechaza() {
        var apagado = new SemanaDelHabito.Dia(DayOfWeek.SATURDAY, LocalTime.of(7, 0), null, true, false,
                RecordatorioDelDia.sinAviso(), false, CombinacionDeAlarma.COMO_HOY, false);

        assertThatThrownBy(() -> cambio(true, null, null, null).aplicarA(habito, null, apagado, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encenderUnaFechaApagadaSoloPorEsaFechaBorraLaFila() {
        var existente = FechaPropia.apagada(habito, fecha);

        assertThat(cambio(true, null, null, null).aplicarA(habito, existente, base, true)).isEmpty();
    }

    @Test
    void restablecerBorraLaFilaAunqueTraigaOtrosCampos() {
        var existente = new FechaPropia(habito, fecha, true, LocalTime.of(9, 0), null, null, null);

        assertThat(CambioDeFecha.restablecer(fecha).aplicarA(habito, existente, base, true)).isEmpty();
    }

    @Test
    void unaHoraFueraDelDiaSeRechaza() {
        assertThatThrownBy(() -> cambio(null, LocalTime.of(23, 59), null, null).aplicarA(habito, null, base, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elCierreSeAcomodaComoSiempre() {
        var conCierre = new CambioDeFecha(fecha, false, null, LocalTime.of(9, 0), LocalTime.of(8, 0), null, null);

        FechaPropia fila = conCierre.aplicarA(habito, null, base, true).orElseThrow();

        assertThat(fila.horaLimite()).isEqualTo(LocalTime.of(23, 50));
        assertThat(Optional.of(fila)).isPresent();
    }
}
