package com.renaser.os.rocks.domain.model.rocasemanal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SemanaProgramaTest {

    @Test
    @DisplayName("inicio en lunes: semana 1 dura la semana completa hasta el domingo")
    void inicioEnLunesSemanaCompleta() {
        LocalDate lunes = LocalDate.of(2026, 8, 24); // lunes
        assertThat(SemanaPrograma.primerDomingoDesde(lunes)).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, lunes)).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 8, 30))).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 8, 31))).isEqualTo(2);
    }

    @Test
    @DisplayName("inicio a mitad de semana: semana 1 es corta (flexible)")
    void inicioAMitadDeSemanaEsCorta() {
        LocalDate miercoles = LocalDate.of(2026, 8, 26); // miercoles
        assertThat(SemanaPrograma.primerDomingoDesde(miercoles)).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(SemanaPrograma.numeroSemanaParaFecha(miercoles, LocalDate.of(2026, 8, 30))).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(miercoles, LocalDate.of(2026, 8, 31))).isEqualTo(2);
    }

    @Test
    @DisplayName("inicio en domingo: el primer domingo es el mismo dia de inicio")
    void inicioEnDomingoEsElMismoDia() {
        LocalDate domingo = LocalDate.of(2026, 8, 23);
        assertThat(SemanaPrograma.primerDomingoDesde(domingo)).isEqualTo(domingo);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(domingo, domingo)).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(domingo, LocalDate.of(2026, 8, 24))).isEqualTo(2);
    }

    @Test
    @DisplayName("semanas avanzan de 7 en 7 despues de la semana 1")
    void semanasAvanzanDeSieteEnSiete() {
        LocalDate lunes = LocalDate.of(2026, 8, 24);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 9, 6))).isEqualTo(2); // domingo semana 2
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 9, 7))).isEqualTo(3); // lunes semana 3
    }

    @Test
    @DisplayName("limites: inicio en miercoles, semana 1 corta va de miercoles al primer domingo")
    void limitesSemana1CortaConInicioAMitadDeSemana() {
        LocalDate miercoles = LocalDate.of(2026, 8, 26);
        var limites = SemanaPrograma.limites(miercoles, 1);
        assertThat(limites.inicio()).isEqualTo(miercoles);
        assertThat(limites.fin()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    @DisplayName("limites: semana 2+ siempre lunes-domingo completa, sin importar el dia de inicio")
    void limitesSemanaCompletaLunesADomingo() {
        LocalDate miercoles = LocalDate.of(2026, 8, 26);
        var limites = SemanaPrograma.limites(miercoles, 2);
        assertThat(limites.inicio()).isEqualTo(LocalDate.of(2026, 8, 31)); // lunes siguiente al primer domingo
        assertThat(limites.fin()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(limites.inicio().getDayOfWeek().getValue()).isEqualTo(1);
        assertThat(limites.fin().getDayOfWeek().getValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("finDelPrograma: dia 90, inclusive de fechaInicio como dia 1")
    void finDelProgramaEsNoventaDias() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        assertThat(SemanaPrograma.finDelPrograma(inicio)).isEqualTo(LocalDate.of(2026, 3, 31)); // 89 dias despues
    }

    /**
     * CARACTERIZACION (riesgos del ajuste de dia, 2026-09-26; pregunta abierta al dueño). Las
     * semanas de rocas se cuentan desde {@code fechaInicio} SIN {@code dias_ajuste_programa},
     * y {@code RocaSemanal} solo admite semanas 1..13 (CHECK de V1). Inicio lunes 2026-06-01,
     * retrocedido 7 dias: su dia 90 cae el 2026-09-05, que para rocks es la semana 14 y ya esta
     * pasado el fin del programa — crear el plan de esas semanas falla con 400.
     */
    @Test
    @DisplayName("caracterizacion: tras retroceder 7 dias, el final del programa cae en la semana 14")
    void caracterizacionRetrocederLlevaElFinalDelProgramaALaSemanaCatorce() {
        LocalDate lunes = LocalDate.of(2026, 6, 1);
        LocalDate diaNoventaConAjuste = lunes.plusDays(90 + 7 - 1);

        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, diaNoventaConAjuste)).isEqualTo(14);
        assertThat(SemanaPrograma.finDelPrograma(lunes)).isBefore(diaNoventaConAjuste);
    }

    /**
     * CARACTERIZACION de un hallazgo SIN ajuste de por medio (preexistente, reportado aparte): con
     * inicio en miercoles la semana 1 dura 5 dias, las semanas 2..13 suman 84, y el dia 90 ya cae
     * en la semana 14. Solo los inicios en lunes o martes caben en 13 semanas.
     */
    @Test
    @DisplayName("caracterizacion: con inicio en miercoles, el dia 90 cae en la semana 14 sin ningun ajuste")
    void caracterizacionInicioEnMiercolesTerminaEnLaSemanaCatorce() {
        LocalDate miercoles = LocalDate.of(2026, 8, 26);

        assertThat(SemanaPrograma.numeroSemanaParaFecha(miercoles, SemanaPrograma.finDelPrograma(miercoles)))
                .isEqualTo(14);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(LocalDate.of(2026, 9, 1), // martes
                SemanaPrograma.finDelPrograma(LocalDate.of(2026, 9, 1)))).isEqualTo(13);
    }
}
