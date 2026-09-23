package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El interruptor ACTIVO/PAUSADO por aprendiz (V23, D-87) — el que antes no guardaba nada.
 */
class DesbloqueoHabitoPausaTest {

    /** La zona del participante, nunca la del servidor (E-91). 15:00Z son las 10:00 en Lima. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final Instant AHORA = Instant.parse("2026-09-04T15:00:00Z");
    private static final Instant DESPUES = AHORA.plusSeconds(3600);

    private static ZonedDateTime enSuZona(Instant instante) {
        return instante.atZone(ZONA);
    }

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

        d.pausar(true, enSuZona(AHORA));

        assertThat(d.estaPausado()).isTrue();
        assertThat(d.pausadoEn()).isEqualTo(AHORA);
        assertThat(d.actualizadoEn()).isEqualTo(AHORA);
    }

    /** Interesa CUANDO dejo de hacerlo, no cuando volvio a tocar el boton. */
    @Test
    void pausarDosVecesNoMueveLaFechaOriginal() {
        DesbloqueoHabito d = activo();
        d.pausar(true, enSuZona(AHORA));

        d.pausar(true, enSuZona(DESPUES));

        assertThat(d.pausadoEn()).isEqualTo(AHORA);
    }

    @Test
    void reactivarLimpiaLaPausa() {
        DesbloqueoHabito d = activo();
        d.pausar(true, enSuZona(AHORA));

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

        assertThatThrownBy(() -> d.pausar(false, enSuZona(AHORA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorio");
        assertThat(d.estaPausado()).isFalse();
    }

    // ---- V31: pausa con fecha de fin ("pausalo hasta el domingo") ----

    /** El dia ANTERIOR a que se toque el boton. AHORA cae el viernes 4 en Lima. */
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 3);
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 4);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 9, 6);
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 7);

    @Test
    void unaPausaConFechaDeFinSigueVigenteHastaEseDiaINCLUSIVE() {
        DesbloqueoHabito d = activo();

        d.pausar(true, DOMINGO, enSuZona(AHORA));

        assertThat(d.estaPausadoEl(VIERNES, ZONA)).isTrue();
        assertThat(d.estaPausadoEl(DOMINGO, ZONA)).as("el ultimo dia todavia cuenta como pausado").isTrue();
    }

    /**
     * La razon de ser del rango: el habito vuelve SOLO. Si esto se rompe, una pausa "hasta el
     * domingo" se convierte en una pausa para siempre, que es justo lo que se queria evitar en un
     * programa de 90 dias.
     */
    @Test
    void desdeElDiaSiguienteElHabitoVuelveSinQueNadieLoToque() {
        DesbloqueoHabito d = activo();

        d.pausar(true, DOMINGO, enSuZona(AHORA));

        assertThat(d.estaPausadoEl(LUNES, ZONA)).isFalse();
        assertThat(d.estaPausado()).as("la pausa sigue REGISTRADA; lo que cambio es el calendario").isTrue();
    }

    @Test
    void unaPausaSinFechaDeFinSigueSiendoIndefinida() {
        DesbloqueoHabito d = activo();

        d.pausar(true, enSuZona(AHORA));

        assertThat(d.estaPausadoEl(VIERNES, ZONA)).isTrue();
        assertThat(d.estaPausadoEl(LUNES.plusYears(1), ZONA)).isTrue();
        assertThat(d.pausadoHasta()).isNull();
    }

    @Test
    void volverAPausarAjustaLaFechaDeFinPeroNoMueveElInicio() {
        DesbloqueoHabito d = activo();
        d.pausar(true, DOMINGO, enSuZona(AHORA));

        d.pausar(true, LUNES, enSuZona(DESPUES));

        assertThat(d.pausadoHasta()).as("se puede extender o acortar una pausa vigente").isEqualTo(LUNES);
        assertThat(d.pausadoEn()).as("cuando dejo de hacerlo no cambia").isEqualTo(AHORA);
    }

    @Test
    void reactivarLimpiaTambienLaFechaDeFin() {
        DesbloqueoHabito d = activo();
        d.pausar(true, DOMINGO, enSuZona(AHORA));

        d.reactivar(DESPUES);

        assertThat(d.estaPausado()).isFalse();
        assertThat(d.pausadoHasta()).isNull();
        assertThat(d.estaPausadoEl(VIERNES, ZONA)).isFalse();
    }

    @Test
    void unHabitoObligatorioTampocoSePuedePausarConFecha() {
        assertThatThrownBy(() -> activo().pausar(false, DOMINGO, enSuZona(AHORA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obligatorio");
    }

    /**
     * LA REGRESION. Hasta el 2026-09-07 `estaPausadoEl` no miraba `pausadoEn`, asi que devolvia
     * true para toda fecha <= `pausadoHasta` — incluidas las ANTERIORES a la pausa. Sintoma que
     * reporto el dueno: pausar "hasta el domingo" apagaba tambien el lunes, el martes y el
     * miercoles de esa semana. Si esto vuelve a ponerse en verde con la comparacion de abajo
     * borrada, el bug volvio.
     */
    @Test
    void unaPausaNoApagaLosDiasANTERIORESaHaberlaPuesto() {
        DesbloqueoHabito d = activo();

        d.pausar(true, DOMINGO, enSuZona(AHORA));

        assertThat(d.estaPausadoEl(VIERNES.minusDays(1), ZONA))
                .as("el dia anterior a tocar el boton no estaba pausado")
                .isFalse();
        assertThat(d.estaPausadoEl(VIERNES.minusDays(3), ZONA)).isFalse();
        assertThat(d.estaPausadoEl(VIERNES, ZONA)).as("el dia en que se pauso, si").isTrue();
    }

    @Test
    void unDesbloqueoActivoNoEstaPausadoNingunDia() {
        assertThat(activo().estaPausadoEl(VIERNES, ZONA)).isFalse();
        assertThat(activo().pausadoHasta()).isNull();
    }

    /**
     * Una pausa INDEFINIDA tampoco mira hacia atras.
     *
     * <p>El extremo de abajo con fecha de fin ya lo cubre
     * {@link #unaPausaNoApagaLosDiasANTERIORESaHaberlaPuesto}; esto no lo repite. Lo que agrega es
     * el caso sin fecha de fin, que es donde la intuicion falla: "indefinida" suena a "siempre", y
     * en un {@code estaPausadoEl} escrito de la forma obvia —{@code pausadoHasta == null} devuelve
     * true y listo— se apagaria tambien todo el pasado del aprendiz. El orden de las guardas es lo
     * que lo evita, y esta prueba lo fija.
     */
    @Test
    void unaPausaIndefinidaTampocoApagaLosDiasANTERIORES() {
        DesbloqueoHabito d = activo();

        d.pausar(true, enSuZona(AHORA));

        assertThat(d.estaPausadoEl(JUEVES, ZONA)).isFalse();
        assertThat(d.estaPausadoEl(VIERNES, ZONA)).isTrue();
    }

    // ---- E-213: volver a pausar despues de que una pausa con fecha ya termino ----

    /** Pausa "hasta el jueves 10", puesta el sabado 5 a las 10:00 de Lima. */
    private static final LocalDate FIN_PAUSA_VIEJA = LocalDate.of(2026, 9, 10);
    private static final Instant PAUSA_VIEJA = Instant.parse("2026-09-05T15:00:00Z");

    /**
     * LA REGRESION de E-213. Hasta el 2026-09-23 {@code pausar} conservaba {@code pausadoEn} siempre
     * que hubiera una pausa REGISTRADA, aunque ya hubiera vencido: la pausa nueva del 20 heredaba el
     * inicio del 5 y, con {@code pausadoHasta} en null, del 11 al 19 -- dias en que el habito SI
     * iba -- pasaban a leerse como pausados.
     */
    @Test
    void pausarDespuesDeQueVencioUnaPausaConFechaArrancaHoyYNoApagaElPasado() {
        DesbloqueoHabito d = activo();
        d.pausar(true, FIN_PAUSA_VIEJA, enSuZona(PAUSA_VIEJA));
        Instant veinte = Instant.parse("2026-09-20T15:00:00Z");

        d.pausar(true, enSuZona(veinte));

        assertThat(d.estaPausadoEl(LocalDate.of(2026, 9, 15), ZONA))
                .as("entre el fin de la pausa vieja y la nueva el habito iba")
                .isFalse();
        assertThat(d.estaPausadoEl(LocalDate.of(2026, 9, 20), ZONA)).isTrue();
        assertThat(d.pausadoEn()).isEqualTo(veinte);
    }

    /** Lo que E-213 NO cambia: extender una pausa que sigue vigente (su ultimo dia) conserva el inicio. */
    @Test
    void extenderUnaPausaVigenteEnSuUltimoDiaConservaElInicio() {
        DesbloqueoHabito d = activo();
        d.pausar(true, FIN_PAUSA_VIEJA, enSuZona(PAUSA_VIEJA));

        d.pausar(true, FIN_PAUSA_VIEJA.plusDays(5), enSuZona(Instant.parse("2026-09-10T20:00:00Z")));

        assertThat(d.pausadoEn()).isEqualTo(PAUSA_VIEJA);
        assertThat(d.pausadoHasta()).isEqualTo(FIN_PAUSA_VIEJA.plusDays(5));
        assertThat(d.estaPausadoEl(LocalDate.of(2026, 9, 8), ZONA)).isTrue();
    }

    /**
     * Regla 02: 03:00 UTC del 11 son las 22:00 del 10 en Lima -- el ultimo dia de la pausa vieja.
     * Comparar contra la fecha UTC (11) daria la pausa por vencida y moveria el inicio.
     */
    @Test
    void laVigenciaSeMideEnElDiaDelParticipanteNoEnUtc() {
        DesbloqueoHabito d = activo();
        d.pausar(true, FIN_PAUSA_VIEJA, enSuZona(PAUSA_VIEJA));

        d.pausar(true, FIN_PAUSA_VIEJA.plusDays(3), enSuZona(Instant.parse("2026-09-11T03:00:00Z")));

        assertThat(d.pausadoEn()).as("en Lima todavia es el 10: la pausa seguia vigente").isEqualTo(PAUSA_VIEJA);
    }

    /** Contraparte del caso anterior: 03:00 UTC del 12 ya es el 11 en Lima, la pausa vieja termino. */
    @Test
    void unDiaDespuesEnSuZonaLaPausaNuevaArrancaDeCero() {
        DesbloqueoHabito d = activo();
        d.pausar(true, FIN_PAUSA_VIEJA, enSuZona(PAUSA_VIEJA));
        Instant nueva = Instant.parse("2026-09-12T03:00:00Z");

        d.pausar(true, FIN_PAUSA_VIEJA.plusDays(3), enSuZona(nueva));

        assertThat(d.pausadoEn()).isEqualTo(nueva);
        assertThat(d.estaPausadoEl(LocalDate.of(2026, 9, 10), ZONA)).isFalse();
        assertThat(d.estaPausadoEl(LocalDate.of(2026, 9, 11), ZONA)).isTrue();
    }
}
