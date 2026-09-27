package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PausaDeMedicionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 22);
    private static final Instant AHORA = Instant.parse("2026-09-22T15:00:00Z");

    private static PausaDeMedicion pausaHasta(LocalDate hasta) {
        return PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), HOY, hasta, AHORA);
    }

    @Test
    void cubreDesdeHoyHastaLaFechaElegidaInclusive() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(3));

        assertThat(pausa.cubre(HOY.minusDays(1))).isFalse();
        assertThat(pausa.cubre(HOY)).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(3))).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(4))).isFalse();
    }

    @Test
    void noSePuedeElegirUnaFechaPasada() {
        assertThatThrownBy(() -> pausaHasta(HOY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alPasarLaFechaVuelveASolo() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(2));

        assertThat(pausa.vigenteEl(HOY.plusDays(2))).isTrue();
        assertThat(pausa.vigenteEl(HOY.plusDays(3))).isFalse();
    }

    /** Reanudar el mismo día que se pausó: ningún día quedó sin medir. */
    @Test
    void reanudarElMismoDiaNoDejaDiasSinMedir() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(5));

        pausa.reanudar(HOY, AHORA);

        assertThat(pausa.cubre(HOY)).isFalse();
        assertThat(pausa.vigenteEl(HOY)).isFalse();
    }

    @Test
    void reanudarAntesDeTiempoMideDesdeEseDia() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(5));

        pausa.reanudar(HOY.plusDays(2), AHORA.plusSeconds(172_800));

        assertThat(pausa.cubre(HOY.plusDays(1))).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(2))).isFalse();
        assertThat(pausa.ultimoDiaPausado()).isEqualTo(HOY.plusDays(1));
    }

    @Test
    void seLePuedeCambiarLaFechaMientrasSigue() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(2));

        pausa.cambiarHasta(HOY.plusDays(9), HOY.plusDays(1));

        assertThat(pausa.cubre(HOY.plusDays(9))).isTrue();
    }

    @Test
    void laPausaDelStaffEsPedidaPorLaPersona() {
        assertThat(pausaHasta(HOY.plusDays(1)).motivo()).isEqualTo(MotivoDePausa.PEDIDA_POR_LA_PERSONA);
        assertThat(pausaHasta(HOY.plusDays(1)).suspensionEnCurso()).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // D-209: los días con la cuenta suspendida no se miden
    // ---------------------------------------------------------------------------------------

    private static PausaDeMedicion suspendidaEl(LocalDate dia) {
        return PausaDeMedicion.porSuspension(PausaId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), dia, AHORA);
    }

    @Test
    void unaSuspensionCubreDesdeElDiaDeLaSuspensionYNoTieneFechaDeRegreso() {
        PausaDeMedicion suspension = suspendidaEl(HOY);

        assertThat(suspension.motivo()).isEqualTo(MotivoDePausa.CUENTA_SUSPENDIDA);
        assertThat(suspension.hasta()).isNull();
        assertThat(suspension.suspensionEnCurso()).isTrue();
        assertThat(suspension.cubre(HOY.minusDays(1))).isFalse();
        assertThat(suspension.cubre(HOY)).isTrue();
        assertThat(suspension.cubre(HOY.plusYears(1))).isTrue();
        assertThat(suspension.ultimoDiaPausado()).isNull();
    }

    /** El día de la reactivación estuvo suspendido una parte: tampoco se mide. Desde el siguiente, sí. */
    @Test
    void elDiaDeLaReactivacionTampocoSeMide() {
        PausaDeMedicion suspension = suspendidaEl(HOY);

        suspension.terminarSuspension(HOY.plusDays(2), AHORA.plusSeconds(172_800));

        assertThat(suspension.suspensionEnCurso()).isFalse();
        assertThat(suspension.cubre(HOY.plusDays(2))).isTrue();
        assertThat(suspension.cubre(HOY.plusDays(3))).isFalse();
        assertThat(suspension.ultimoDiaPausado()).isEqualTo(HOY.plusDays(2));
        assertThat(suspension.reanudadaEl()).isEqualTo(HOY.plusDays(3));
        assertThat(suspension.reanudadaEn()).isEqualTo(AHORA.plusSeconds(172_800));
    }

    @Test
    void suspenderYReactivarElMismoDiaDejaEseDiaSinMedir() {
        PausaDeMedicion suspension = suspendidaEl(HOY);

        suspension.terminarSuspension(HOY, AHORA.plusSeconds(600));

        assertThat(suspension.cubre(HOY)).isTrue();
        assertThat(suspension.cubre(HOY.plusDays(1))).isFalse();
    }

    /** Solo con un cambio de zona en el medio la reactivación podría caer antes: cubre al menos su primer día. */
    @Test
    void unaReactivacionQueCaeAntesDeLaSuspensionCubreAlMenosElDiaDeLaSuspension() {
        PausaDeMedicion suspension = suspendidaEl(HOY);

        suspension.terminarSuspension(HOY.minusDays(1), AHORA.plusSeconds(60));

        assertThat(suspension.cubre(HOY)).isTrue();
        assertThat(suspension.cubre(HOY.plusDays(1))).isFalse();
    }

    /** No es una pausa de la persona: no se muestra como vigente ni se cambia desde «pausar mi semáforo». */
    @Test
    void unaSuspensionNoEsUnaPausaVigenteNiSeTocaAMano() {
        PausaDeMedicion suspension = suspendidaEl(HOY);

        assertThat(suspension.vigenteEl(HOY)).isFalse();
        assertThatThrownBy(() -> suspension.cambiarHasta(HOY.plusDays(3), HOY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> suspension.reanudar(HOY, AHORA)).isInstanceOf(IllegalStateException.class);
        assertThat(suspension.suspensionEnCurso()).isTrue();
    }

    @Test
    void unaSuspensionTerminadaNoSeTerminaDosVecesNiUnaPausaSeTerminaComoSuspension() {
        PausaDeMedicion suspension = suspendidaEl(HOY);
        suspension.terminarSuspension(HOY.plusDays(1), AHORA);

        assertThatThrownBy(() -> suspension.terminarSuspension(HOY.plusDays(5), AHORA))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pausaHasta(HOY.plusDays(2)).terminarSuspension(HOY, AHORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaPausaTerminadaNoSeTocaMas() {
        PausaDeMedicion pausa = pausaHasta(HOY);
        pausa.reanudar(HOY, AHORA);

        assertThatThrownBy(() -> pausa.cambiarHasta(HOY.plusDays(3), HOY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pausa.reanudar(HOY, AHORA)).isInstanceOf(IllegalStateException.class);
    }
}
