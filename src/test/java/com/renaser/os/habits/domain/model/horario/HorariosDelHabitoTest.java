package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-200: al retroceder el dia, un habito que ya corrio desde el inicio de su horario sigue activo y
 * se resuelve como en ese primer dia; lo que nunca corrio sigue esperando.
 */
class HorariosDelHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());

    private static HorarioHabito horario(int inicio, Integer fin, TipoDia tipo, int hora) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HABITO, inicio, fin, tipo,
                LocalTime.of(hora, 0), null, AHORA);
    }

    /** Como nace un habito PERSONAL creado el dia 30 ({@code MisHabitosService.crear}). */
    private static final HorarioHabito PERSONAL_DEL_DIA_30 = horario(30, null, TipoDia.TODOS, 7);

    @Test
    @DisplayName("un habito que nunca corrio sigue esperando su dia")
    void loQueNuncaCorrioSigueEsperando() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(PERSONAL_DEL_DIA_30));

        assertThat(horarios.diaEfectivo(25, TipoDia.DISCIPLINA, null)).isEqualTo(25);
        assertThat(horarios.vigentesEn(25, TipoDia.DISCIPLINA)).isEmpty();
        assertThat(horarios.diasParaArrancar(25, null)).isEqualTo(5);
    }

    @Test
    @DisplayName("un habito que ya corrio desde su inicio se resuelve como en su primer dia")
    void loQueYaCorrioSeResuelveComoEnSuPrimerDia() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(PERSONAL_DEL_DIA_30));

        int dia = horarios.diaEfectivo(25, TipoDia.DISCIPLINA, 32);

        assertThat(dia).isEqualTo(30);
        assertThat(horarios.vigentesEn(dia, TipoDia.DISCIPLINA)).containsExactly(PERSONAL_DEL_DIA_30);
        assertThat(horarios.diasParaArrancar(25, 32)).isZero();
        assertThat(horarios.diasParaArrancar(25, 30)).as("haber corrido justo el dia de inicio alcanza").isZero();
    }

    @Test
    @DisplayName("registros anteriores al inicio del horario no cuentan como que corrio")
    void registrosAnterioresAlInicioNoCuentan() {
        HorariosDelHabito audioterapia = HorariosDelHabito.de(List.of(horario(11, 90, TipoDia.TODOS, 7)));

        assertThat(audioterapia.diaEfectivo(9, TipoDia.DISCIPLINA, 10)).isEqualTo(9);
        assertThat(audioterapia.diasParaArrancar(9, 10)).isEqualTo(2);
    }

    @Test
    @DisplayName("sin retroceso nada cambia: el dia que el horario cubre se resuelve con si mismo")
    void sinRetrocesoNadaCambia() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(PERSONAL_DEL_DIA_30));

        assertThat(horarios.diaEfectivo(31, TipoDia.DISCIPLINA, 30)).isEqualTo(31);
        assertThat(horarios.diaEfectivoDeUnRegistro(31, TipoDia.DISCIPLINA)).isEqualTo(31);
        assertThat(horarios.necesitaSaberSiYaCorrio(31, TipoDia.DISCIPLINA)).isFalse();
    }

    @Test
    @DisplayName("con varios tramos, el que cubre el dia manda; el hueco se llena solo con el que ya corrio")
    void conVariosTramosElQueCubreElDiaManda() {
        HorarioHabito primero = horario(1, 10, TipoDia.TODOS, 6);
        HorarioHabito segundo = horario(20, 90, TipoDia.TODOS, 8);
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(segundo, primero));

        assertThat(horarios.diaEfectivo(5, TipoDia.DISCIPLINA, 25)).isEqualTo(5);
        assertThat(horarios.vigentesEn(5, TipoDia.DISCIPLINA)).containsExactly(primero);
        assertThat(horarios.diaEfectivo(15, TipoDia.DISCIPLINA, 25)).isEqualTo(20);
        assertThat(horarios.diaEfectivo(15, TipoDia.DISCIPLINA, 12)).as("el segundo nunca corrio").isEqualTo(15);
    }

    @Test
    @DisplayName("un horario de domingo que ya corrio vuelve solo los domingos")
    void unHorarioDeDomingoVuelveSoloLosDomingos() {
        HorariosDelHabito domingo = HorariosDelHabito.de(List.of(horario(35, null, TipoDia.DOMINGO, 7)));

        assertThat(domingo.diaEfectivo(30, TipoDia.DISCIPLINA, 35)).isEqualTo(30);
        assertThat(domingo.necesitaSaberSiYaCorrio(30, TipoDia.DISCIPLINA)).isFalse();
        assertThat(domingo.diaEfectivo(30, TipoDia.DOMINGO, 35)).isEqualTo(35);
        assertThat(domingo.diasParaArrancar(30, 35)).isZero();
    }

    @Test
    @DisplayName("un registro generado por debajo del inicio se lee con el dia con el que se genero")
    void unRegistroGeneradoPorDebajoSeLeeComoSeGenero() {
        HorarioHabito primero = horario(1, 10, TipoDia.TODOS, 6);
        HorarioHabito segundo = horario(20, 90, TipoDia.TODOS, 8);
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(primero, segundo));

        assertThat(horarios.diaEfectivoDeUnRegistro(15, TipoDia.DISCIPLINA))
                .isEqualTo(horarios.diaEfectivo(15, TipoDia.DISCIPLINA, 25)).isEqualTo(20);
        assertThat(horarios.diaEfectivoDeUnRegistro(5, TipoDia.DISCIPLINA)).isEqualTo(5);
    }

    @Test
    @DisplayName("solo hace falta leer los registros cuando el dia quedo por debajo de un inicio")
    void soloHaceFaltaLeerLosRegistrosPorDebajoDeUnInicio() {
        HorariosDelHabito personal = HorariosDelHabito.de(List.of(PERSONAL_DEL_DIA_30));
        HorariosDelHabito cerrado = HorariosDelHabito.de(List.of(horario(1, 34, TipoDia.DISCIPLINA, 20)));

        assertThat(personal.necesitaSaberSiYaCorrio(25, TipoDia.DISCIPLINA)).isTrue();
        assertThat(cerrado.necesitaSaberSiYaCorrio(40, TipoDia.DISCIPLINA)).as("paso su fin, no su inicio")
                .isFalse();
        assertThat(HorariosDelHabito.de(List.of()).necesitaSaberSiYaCorrio(25, TipoDia.DISCIPLINA)).isFalse();
    }

    @Test
    @DisplayName("antes del Dia 1 no hay retroceso: nada se resuelve por encima")
    void antesDelDiaUnoNoHayRetroceso() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(horario(1, 90, TipoDia.TODOS, 7)));

        assertThat(horarios.diaEfectivo(0, TipoDia.DISCIPLINA, 40)).isZero();
        assertThat(horarios.diaEfectivoDeUnRegistro(0, TipoDia.DISCIPLINA)).isZero();
        assertThat(horarios.necesitaSaberSiYaCorrio(0, TipoDia.DISCIPLINA)).isFalse();
    }

    @Test
    @DisplayName("sin horarios no hay primer dia ni candado")
    void sinHorariosNoHayPrimerDiaNiCandado() {
        HorariosDelHabito sinHorarios = HorariosDelHabito.de(List.of());

        assertThat(sinHorarios.primerDia()).isEmpty();
        assertThat(sinHorarios.diasParaArrancar(5, null)).isZero();
        assertThat(sinHorarios.diaEfectivo(5, TipoDia.DISCIPLINA, null)).isEqualTo(5);
    }
}
