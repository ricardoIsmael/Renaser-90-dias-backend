package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-288 (spec 014): los unicos dias que se pueden volver a leer son los CERRADOS de la semana en curso (lunes a
 * domingo), de ayer hacia atras hasta el lunes y nunca antes del Dia 1. Dominio puro: la fecha de "hoy" es la de la
 * persona y llega resuelta; el reloj en la madrugada UTC se prueba en {@code DiaCerradoConsultaTest}.
 *
 * <p>Calendario de octubre y noviembre de 2026: lunes 26/10, miercoles 28/10, viernes 30/10, domingo 1/11, lunes 2/11.
 */
class DiasCerradosDeLaSemanaTest {

    private static final LocalDate PROGRAMA_VIEJO = LocalDate.of(2026, 9, 1);

    private static final LocalDate LUNES_26 = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES_27 = LocalDate.of(2026, 10, 27);
    private static final LocalDate MIERCOLES_28 = LocalDate.of(2026, 10, 28);
    private static final LocalDate JUEVES_29 = LocalDate.of(2026, 10, 29);
    private static final LocalDate VIERNES_30 = LocalDate.of(2026, 10, 30);
    private static final LocalDate SABADO_31 = LocalDate.of(2026, 10, 31);
    private static final LocalDate DOMINGO_1_NOV = LocalDate.of(2026, 11, 1);
    private static final LocalDate LUNES_2_NOV = LocalDate.of(2026, 11, 2);

    private static void sePuedeVer(LocalDate hoy, LocalDate diaUno, LocalDate fecha) {
        assertThatCode(() -> DiasCerradosDeLaSemana.de(hoy, diaUno).exigirQueIncluya(fecha)).doesNotThrowAnyException();
    }

    private static void noSePuedeVer(LocalDate hoy, LocalDate diaUno, LocalDate fecha, String mensaje) {
        assertThatThrownBy(() -> DiasCerradosDeLaSemana.de(hoy, diaUno).exigirQueIncluya(fecha))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(mensaje);
    }

    @Test
    @DisplayName("miercoles: ayer y el lunes si; hoy, mañana y el domingo anterior no")
    void aMitadDeSemana() {
        sePuedeVer(MIERCOLES_28, PROGRAMA_VIEJO, MARTES_27);
        sePuedeVer(MIERCOLES_28, PROGRAMA_VIEJO, LUNES_26);
        noSePuedeVer(MIERCOLES_28, PROGRAMA_VIEJO, MIERCOLES_28, "todavía no cerró");
        noSePuedeVer(MIERCOLES_28, PROGRAMA_VIEJO, JUEVES_29, "todavía no cerró");
        noSePuedeVer(MIERCOLES_28, PROGRAMA_VIEJO, LUNES_26.minusDays(1), "esta semana");
    }

    @Test
    @DisplayName("lunes: ningun dia sirve, ni ayer (el domingo de la semana anterior)")
    void elLunesNoHayNingunDiaCerradoDeLaSemana() {
        noSePuedeVer(LUNES_2_NOV, PROGRAMA_VIEJO, DOMINGO_1_NOV, "esta semana");
        noSePuedeVer(LUNES_2_NOV, PROGRAMA_VIEJO, LUNES_2_NOV, "todavía no cerró");
        noSePuedeVer(LUNES_2_NOV, PROGRAMA_VIEJO, LUNES_2_NOV.plusDays(1), "todavía no cerró");
    }

    @Test
    @DisplayName("domingo: de ayer hasta el lunes; el domingo anterior ya es otra semana")
    void elDomingoSeVeLaSemanaEntera() {
        sePuedeVer(DOMINGO_1_NOV, PROGRAMA_VIEJO, SABADO_31);
        sePuedeVer(DOMINGO_1_NOV, PROGRAMA_VIEJO, LUNES_26);
        noSePuedeVer(DOMINGO_1_NOV, PROGRAMA_VIEJO, DOMINGO_1_NOV, "todavía no cerró");
        noSePuedeVer(DOMINGO_1_NOV, PROGRAMA_VIEJO, LocalDate.of(2026, 10, 25), "esta semana");
    }

    @Test
    @DisplayName("Dia 1 a mitad de semana: nada antes de el, aunque sea de esta semana")
    void nuncaAntesDelDiaUno() {
        LocalDate diaUno = MIERCOLES_28;
        sePuedeVer(VIERNES_30, diaUno, MIERCOLES_28);
        sePuedeVer(VIERNES_30, diaUno, JUEVES_29);
        noSePuedeVer(VIERNES_30, diaUno, MARTES_27, "Día 1");
        noSePuedeVer(VIERNES_30, diaUno, LUNES_26, "Día 1");
        noSePuedeVer(VIERNES_30, diaUno, VIERNES_30, "todavía no cerró");
    }

    @Test
    @DisplayName("el Dia 1 es hoy: no hay ayer que ver")
    void elPrimerDiaNoTieneDiasCerrados() {
        noSePuedeVer(MIERCOLES_28, MIERCOLES_28, MARTES_27, "Día 1");
        noSePuedeVer(MIERCOLES_28, MIERCOLES_28, MIERCOLES_28, "todavía no cerró");
    }

    @Test
    @DisplayName("el Dia 1 es en el futuro: todo lo cerrado es anterior")
    void conElDiaUnoPorVenirNoHayNada() {
        noSePuedeVer(MIERCOLES_28, VIERNES_30, MARTES_27, "Día 1");
    }

    @Test
    @DisplayName("otra semana, aunque ya cerrada, no se lee por aca")
    void otraSemanaNo() {
        noSePuedeVer(VIERNES_30, PROGRAMA_VIEJO, LocalDate.of(2026, 10, 19), "esta semana");
        noSePuedeVer(VIERNES_30, PROGRAMA_VIEJO, PROGRAMA_VIEJO, "esta semana");
    }

    @Test
    @DisplayName("sin Dia 1 elegido no hay ningun dia que ver")
    void sinDiaUnoNoHayNada() {
        noSePuedeVer(VIERNES_30, null, MARTES_27, "no empezaste");
    }

    @Test
    @DisplayName("sin fecha se rechaza, y sin 'hoy' no se puede ni armar la regla")
    void faltanDatos() {
        noSePuedeVer(VIERNES_30, PROGRAMA_VIEJO, null, "Falta la fecha");
        assertThatThrownBy(() -> DiasCerradosDeLaSemana.de(null, PROGRAMA_VIEJO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el rechazo es siempre IllegalArgumentException: el GlobalExceptionHandler lo responde 400")
    void elRechazoEsUnaValidacion() {
        assertThatThrownBy(() -> DiasCerradosDeLaSemana.de(VIERNES_30, PROGRAMA_VIEJO).exigirQueIncluya(VIERNES_30))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
