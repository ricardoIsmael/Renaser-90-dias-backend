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

    /** D-122: ya no se rechaza — `PreferenciaHorario.crear` acomoda el cierre antes de llegar aca. */
    @Test
    void unCierreAnteriorALaHoraDeDisparoLlegaYaAcomodado() {
        var martes = new HorarioSemanal(DayOfWeek.TUESDAY, preferencia(LocalTime.of(7, 0), LocalTime.of(6, 0)));

        assertThat(martes.preferencia().horaLimite()).isEqualTo(LocalTime.of(23, 50));
    }

    /** La guarda defensiva, con lo unico que la dispara: una fila rehidratada de la base. */
    @Test
    void unaFilaViejaDeLaBaseConLaVentanaVaciaSigueSiendoRechazada() {
        var cruda = PreferenciaHorario.rehydrate(actor, habito, LocalTime.of(7, 0), LocalTime.of(6, 0), true, null,
                AHORA, AHORA);

        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.TUESDAY, cruda))
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

    // ─── Spec 008 (V111, D-282): el aviso propio del dia ────────────────────────────────────

    /** Un dia puede traer solo su aviso y heredar la hora general: la fila de «solo aviso». */
    @Test
    void unDiaEncendidoPuedeTenerSoloAvisoPropioSinHora() {
        var sabado = new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true,
                RecordatorioDelDia.con(java.util.List.of(30)));

        assertThat(sabado.preferencia().horaDisparo()).isNull();
        assertThat(sabado.tieneHoraPropia()).isFalse();
        assertThat(sabado.recordatorio()).isEqualTo(RecordatorioDelDia.con(java.util.List.of(30)));
    }

    /** «Sin aviso ese dia» tambien es algo propio: no se confunde con heredar. */
    @Test
    void sinAvisoPropioCuentaComoAlgoPropio() {
        var sabado = new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true,
                RecordatorioDelDia.sinAviso());

        assertThat(sabado.recordatorio().activo()).isFalse();
    }

    /** Encendido sin hora y sin aviso no dice nada que lo general no diga: se rechaza como siempre. */
    @Test
    void encendidoSinHoraNiAvisoSigueRechazado() {
        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("horaDisparo");
    }

    @Test
    void apagadoSinNadaSigueSiendoValidoYConservaSuAviso() {
        var apagado = new HorarioSemanal(DayOfWeek.TUESDAY, preferencia(null, null), false,
                RecordatorioDelDia.con(java.util.List.of(10)));

        assertThat(apagado.activo()).isFalse();
        assertThat(apagado.recordatorio()).isNotNull();
    }

    @Test
    void lasFirmasHistoricasNoTienenAvisoPropio() {
        assertThat(new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null)).recordatorio())
                .isNull();
        assertThat(new HorarioSemanal(DayOfWeek.MONDAY, preferencia(null, null), false).recordatorio()).isNull();
    }

    @Test
    void conRecordatorioDevuelveOtroDiaConEseAvisoYQuitarlo() {
        var lunes = new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null));

        var conAviso = lunes.conRecordatorio(RecordatorioDelDia.sinAviso());
        assertThat(conAviso.recordatorio()).isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(conAviso.preferencia().horaDisparo()).isEqualTo(LocalTime.of(5, 0));
        assertThat(conAviso.conRecordatorio(null).recordatorio()).isNull();
    }

    /** La regla que el adaptador usa para decidir si borra la fila que se quedo sin nada propio. */
    @Test
    void dejaAlgoPropioEsLaMismaReglaDelConstructor() {
        assertThat(HorarioSemanal.dejaAlgoPropio(true, null, null)).isFalse();
        assertThat(HorarioSemanal.dejaAlgoPropio(true, LocalTime.of(5, 0), null)).isTrue();
        assertThat(HorarioSemanal.dejaAlgoPropio(true, null, RecordatorioDelDia.sinAviso())).isTrue();
        assertThat(HorarioSemanal.dejaAlgoPropio(false, null, null)).isTrue();
    }

    // ─── Spec 009 (V112, D-283): la combinacion de alarma propia del dia ────────────────────

    /** Una fila puede traer solo como suena: hereda la hora y el aviso generales. */
    @Test
    void unDiaEncendidoPuedeTenerSoloLaCombinacionDeAlarma() {
        var sabado = new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true, null,
                AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false)));

        assertThat(sabado.tieneHoraPropia()).isFalse();
        assertThat(sabado.recordatorio()).isNull();
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false)));
    }

    @Test
    void encendidoSinHoraAvisoNiAlarmaSigueRechazado() {
        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("horaDisparo");
    }

    /** Un ajuste que no dice nada no es «algo propio»: heredarlo todo es no tener nada. */
    @Test
    void unAjusteVacioNoCuentaComoAlgoPropio() {
        assertThatThrownBy(() -> new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(null, null), true, null,
                AjusteDeAlarma.hereda())).isInstanceOf(NullPointerException.class);
        assertThat(HorarioSemanal.dejaAlgoPropio(true, null, null, AjusteDeAlarma.hereda())).isFalse();
        assertThat(new HorarioSemanal(DayOfWeek.SATURDAY, preferencia(LocalTime.of(9, 0), null), true, null,
                AjusteDeAlarma.hereda()).alarma()).as("un ajuste vacio se guarda como ninguno").isNull();
    }

    @Test
    void dejaAlgoPropioCuentaLaAlarma() {
        var propia = AjusteDeAlarma.de(CombinacionDeAlarma.de(true, false, false, true));

        assertThat(HorarioSemanal.dejaAlgoPropio(true, null, null, propia)).isTrue();
        assertThat(HorarioSemanal.dejaAlgoPropio(true, null, null, null)).isFalse();
        assertThat(HorarioSemanal.dejaAlgoPropio(false, null, null, null)).isTrue();
    }

    @Test
    void lasFirmasAnterioresNoTienenAlarmaPropia() {
        assertThat(new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null)).alarma()).isNull();
        assertThat(new HorarioSemanal(DayOfWeek.MONDAY, preferencia(null, null), false).alarma()).isNull();
        assertThat(new HorarioSemanal(DayOfWeek.MONDAY, preferencia(null, null), true,
                RecordatorioDelDia.sinAviso()).alarma()).isNull();
    }

    @Test
    void conAlarmaDevuelveOtroDiaConEsaAlarmaSinTocarLoDemasYQuitarlaLaDeja() {
        var lunes = new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null), true,
                RecordatorioDelDia.sinAviso());
        var propia = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false));

        var conAlarma = lunes.conAlarma(propia);

        assertThat(conAlarma.alarma()).isEqualTo(propia);
        assertThat(conAlarma.preferencia().horaDisparo()).isEqualTo(LocalTime.of(5, 0));
        assertThat(conAlarma.recordatorio()).isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(conAlarma.conAlarma(null).alarma()).isNull();
    }

    @Test
    void conRecordatorioConservaLaAlarma() {
        var propia = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false));
        var lunes = new HorarioSemanal(DayOfWeek.MONDAY, preferencia(LocalTime.of(5, 0), null), true, null, propia);

        assertThat(lunes.conRecordatorio(RecordatorioDelDia.sinAviso()).alarma()).isEqualTo(propia);
    }
}
