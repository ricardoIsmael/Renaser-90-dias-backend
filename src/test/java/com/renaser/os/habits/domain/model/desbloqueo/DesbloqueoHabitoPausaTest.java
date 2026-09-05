package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El interruptor ACTIVO/PAUSADO por aprendiz (V23, D-87) — el que antes no guardaba nada.
 */
class DesbloqueoHabitoPausaTest {

    private static final Instant AHORA = Instant.parse("2026-09-04T15:00:00Z");
    private static final Instant DESPUES = AHORA.plusSeconds(3600);

    private static DesbloqueoHabito activo() {
        return DesbloqueoHabito.rehydrate(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                1, AHORA, AHORA, AHORA);
    }

    @Test
    void unDesbloqueoSinPausaRegistradaEstaActivo() {
        assertThat(activo().estaPausado()).isFalse();
        assertThat(activo().pausadoEn()).isNull();
    }

    @Test
    void pausarGuardaCuandoSePauso() {
        DesbloqueoHabito d = activo();

        d.pausar(true, AHORA);

        assertThat(d.estaPausado()).isTrue();
        assertThat(d.pausadoEn()).isEqualTo(AHORA);
        assertThat(d.actualizadoEn()).isEqualTo(AHORA);
    }

    /** Interesa CUANDO dejo de hacerlo, no cuando volvio a tocar el boton. */
    @Test
    void pausarDosVecesNoMueveLaFechaOriginal() {
        DesbloqueoHabito d = activo();
        d.pausar(true, AHORA);

        d.pausar(true, DESPUES);

        assertThat(d.pausadoEn()).isEqualTo(AHORA);
    }

    @Test
    void reactivarLimpiaLaPausa() {
        DesbloqueoHabito d = activo();
        d.pausar(true, AHORA);

        d.reactivar(DESPUES);

        assertThat(d.estaPausado()).isFalse();
        assertThat(d.pausadoEn()).isNull();
        assertThat(d.actualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    void reactivarAlgoYaActivoNoCambiaNada() {
        DesbloqueoHabito d = activo();

        d.reactivar(DESPUES);

        assertThat(d.estaPausado()).isFalse();
        assertThat(d.actualizadoEn()).isEqualTo(AHORA);
    }

    /**
     * Si un habito obligatorio se pudiera pausar, "obligatorio" no querria decir nada. La
     * invariante cruza dos tablas (`desbloqueos_habito` y `habitos.desactivable`), asi que el
     * llamador aporta el dato y el dominio impone la regla.
     */
    @Test
    void unHabitoObligatorioNoSePuedePausar() {
        DesbloqueoHabito d = activo();

        assertThatThrownBy(() -> d.pausar(false, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorio");
        assertThat(d.estaPausado()).isFalse();
    }

    // ---- V31: pausa con fecha de fin ("pausalo hasta el domingo") ----

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 4);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 9, 6);
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);

    @Test
    void unaPausaConFechaDeFinSigueVigenteHastaEseDiaINCLUSIVE() {
        DesbloqueoHabito d = activo();

        d.pausar(true, DOMINGO, AHORA);

        assertThat(d.estaPausadoEl(VIERNES)).isTrue();
        assertThat(d.estaPausadoEl(DOMINGO)).as("el ultimo dia todavia cuenta como pausado").isTrue();
    }

    /**
     * La razon de ser del rango: el habito vuelve SOLO. Si esto se rompe, una pausa "hasta el
     * domingo" se convierte en una pausa para siempre, que es justo lo que se queria evitar en un
     * programa de 90 dias.
     */
    @Test
    void desdeElDiaSiguienteElHabitoVuelveSinQueNadieLoToque() {
        DesbloqueoHabito d = activo();

        d.pausar(true, DOMINGO, AHORA);

        assertThat(d.estaPausadoEl(LUNES)).isFalse();
        assertThat(d.estaPausado()).as("la pausa sigue REGISTRADA; lo que cambio es el calendario").isTrue();
    }

    @Test
    void unaPausaSinFechaDeFinSigueSiendoIndefinida() {
        DesbloqueoHabito d = activo();

        d.pausar(true, AHORA);

        assertThat(d.estaPausadoEl(VIERNES)).isTrue();
        assertThat(d.estaPausadoEl(LUNES.plusYears(1))).isTrue();
        assertThat(d.pausadoHasta()).isNull();
    }

    @Test
    void volverAPausarAjustaLaFechaDeFinPeroNoMueveElInicio() {
        DesbloqueoHabito d = activo();
        d.pausar(true, DOMINGO, AHORA);

        d.pausar(true, LUNES, DESPUES);

        assertThat(d.pausadoHasta()).as("se puede extender o acortar una pausa vigente").isEqualTo(LUNES);
        assertThat(d.pausadoEn()).as("cuando dejo de hacerlo no cambia").isEqualTo(AHORA);
    }

    @Test
    void reactivarLimpiaTambienLaFechaDeFin() {
        DesbloqueoHabito d = activo();
        d.pausar(true, DOMINGO, AHORA);

        d.reactivar(DESPUES);

        assertThat(d.estaPausado()).isFalse();
        assertThat(d.pausadoHasta()).isNull();
        assertThat(d.estaPausadoEl(VIERNES)).isFalse();
    }

    @Test
    void unHabitoObligatorioTampocoSePuedePausarConFecha() {
        assertThatThrownBy(() -> activo().pausar(false, DOMINGO, AHORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    void unDesbloqueoActivoNoEstaPausadoNingunDia() {
        assertThat(activo().estaPausadoEl(VIERNES)).isFalse();
        assertThat(activo().pausadoHasta()).isNull();
    }
}
