package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 008 (D-282): el aviso propio de un dia de la semana. La diferencia que importa es entre
 * «hereda el general» (no hay objeto) y «sin aviso ese dia» ({@link RecordatorioDelDia#sinAviso()}).
 */
class RecordatorioDelDiaTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T10:00:00Z");
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());

    @Test
    void activoGuardaLasAntelacionesDeLaMasTempranaALaMasTardia() {
        var aviso = RecordatorioDelDia.con(List.of(0, 30, 10));

        assertThat(aviso.activo()).isTrue();
        assertThat(aviso.antelaciones()).containsExactly(30, 10, 0);
        assertThat(aviso.minutosMasTemprana()).isEqualTo(30);
    }

    @Test
    void activoSinNingunaAntelacionSeRechaza() {
        assertThatThrownBy(() -> RecordatorioDelDia.con(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("antelacion");
        assertThatThrownBy(() -> RecordatorioDelDia.con(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaAntelacionFueraDelRangoSeRechazaConElRango() {
        assertThatThrownBy(() -> RecordatorioDelDia.con(List.of(-5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0 y");
        assertThatThrownBy(() -> RecordatorioDelDia.con(List.of(99999)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0 y");
    }

    @Test
    void sinAvisoIgnoraLaListaYNoTieneAntelaciones() {
        var sin = RecordatorioDelDia.sinAviso();

        assertThat(sin.activo()).isFalse();
        assertThat(sin.antelaciones()).isEmpty();
        assertThat(sin.minutosMasTemprana()).isNull();
        assertThat(new RecordatorioDelDia(false, List.of(30, 10))).isEqualTo(sin);
    }

    @Test
    void delGeneralLeeElConjuntoSiSeConoceYSiNoLosMinutosSueltos() {
        var conConjunto = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);
        conConjunto.actualizarRecordatorioConAntelaciones(true, List.of(0, 30), AHORA);
        var soloMinutos = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);
        soloMinutos.actualizarRecordatorio(true, 10, AHORA);

        assertThat(RecordatorioDelDia.delGeneral(conConjunto)).isEqualTo(RecordatorioDelDia.con(List.of(30, 0)));
        assertThat(RecordatorioDelDia.delGeneral(soloMinutos)).isEqualTo(RecordatorioDelDia.con(List.of(10)));
    }

    @Test
    void delGeneralEsSinAvisoSiNoHayPreferenciaOEstaApagadoOSinMinutos() {
        var apagado = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);
        apagado.actualizarRecordatorio(false, null, AHORA);
        var activoSinMinutos = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);

        assertThat(RecordatorioDelDia.delGeneral(null)).isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(RecordatorioDelDia.delGeneral(apagado)).isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(RecordatorioDelDia.delGeneral(activoSinMinutos)).isEqualTo(RecordatorioDelDia.sinAviso());
    }

    @Test
    void dosAvisosConLasMismasAntelacionesSonIguales() {
        assertThat(RecordatorioDelDia.con(List.of(10, 30))).isEqualTo(RecordatorioDelDia.con(List.of(30, 10, 10)));
        assertThat(RecordatorioDelDia.con(List.of(30))).isNotEqualTo(RecordatorioDelDia.con(List.of(30, 10)));
        assertThat(RecordatorioDelDia.con(List.of(0))).isNotEqualTo(RecordatorioDelDia.sinAviso());
    }
}
