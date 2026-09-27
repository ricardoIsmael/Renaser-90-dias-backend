package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-200: al retroceder el dia, un habito que ya corrio desde el inicio de su horario sigue activo y
 * se resuelve como en ese primer dia; lo que nunca corrio sigue esperando. D-216: el primer dia de un
 * habito PERSONAL cuenta como alcanzado desde que se crea.
 */
class HorariosDelHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());

    private static HorarioHabito horario(int inicio, Integer fin, TipoDia tipo, int hora) {
        return horarioDe(HABITO, inicio, fin, tipo, hora);
    }

    private static HorarioHabito horarioDe(HabitoId habito, int inicio, Integer fin, TipoDia tipo, int hora) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito, inicio, fin, tipo,
                LocalTime.of(hora, 0), null, AHORA);
    }

    /**
     * Un horario abierto que arranca el dia 30. Con {@link HorariosDelHabito#de(List)} —el catalogo, o
     * un habito del que no se sabe de quien es—, que ya corrio solo lo dicen los registros.
     *
     * <p><b>Corregido 2026-09-27 (D-216).</b> Se llamaba {@code PERSONAL_DEL_DIA_30} y decia "como
     * nace un habito PERSONAL creado el dia 30". El horario es el mismo, pero un habito personal ahora
     * se arma con {@link HorariosDelHabito#de(Habito, List)} y su primer dia ya cuenta como alcanzado
     * (ver las pruebas de D-216 al final).
     */
    private static final HorarioHabito DESDE_EL_DIA_30 = horario(30, null, TipoDia.TODOS, 7);

    private static Habito personal() {
        return Habito.crearPersonal(HABITO, UserId.of(UUID.randomUUID()), "Caminata E2E", TipoHabito.CHECKBOX,
                "CUERPO", PlantillaHabitoPersonal.OTRO, null, AHORA);
    }

    private static Habito delCatalogo(HabitoId id) {
        return Habito.crearDeSistema(id, "AUDIOTERAPIA SEMANAL", TipoHabito.CHECKBOX,
                new DetallesHabito(null, "MENTE", ExigenciaEvidencia.OPCIONAL, false, false), AHORA);
    }

    @Test
    @DisplayName("un habito que nunca corrio sigue esperando su dia")
    void loQueNuncaCorrioSigueEsperando() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(DESDE_EL_DIA_30));

        assertThat(horarios.diaEfectivo(25, TipoDia.DISCIPLINA, null)).isEqualTo(25);
        assertThat(horarios.vigentesEn(25, TipoDia.DISCIPLINA)).isEmpty();
        assertThat(horarios.diasParaArrancar(25, null)).isEqualTo(5);
    }

    @Test
    @DisplayName("un habito que ya corrio desde su inicio se resuelve como en su primer dia")
    void loQueYaCorrioSeResuelveComoEnSuPrimerDia() {
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(DESDE_EL_DIA_30));

        int dia = horarios.diaEfectivo(25, TipoDia.DISCIPLINA, 32);

        assertThat(dia).isEqualTo(30);
        assertThat(horarios.vigentesEn(dia, TipoDia.DISCIPLINA)).containsExactly(DESDE_EL_DIA_30);
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
        HorariosDelHabito horarios = HorariosDelHabito.de(List.of(DESDE_EL_DIA_30));

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
        HorariosDelHabito personal = HorariosDelHabito.de(List.of(DESDE_EL_DIA_30));
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

    // ---- D-216 (TZ-15 del e2e, 2026-09-27): un habito PERSONAL corre desde que se crea ----

    @Test
    @DisplayName("D-216: un habito personal creado el dia 30 sigue activo al bajar al 25 aunque no tenga registros")
    void unHabitoPersonalCorreDesdeQueSeCreaAunqueNoTengaRegistros() {
        HorariosDelHabito caminata = HorariosDelHabito.de(personal(), List.of(DESDE_EL_DIA_30));

        assertThat(caminata.diasParaArrancar(25, null)).as("sin candado").isZero();
        assertThat(caminata.diaEfectivo(25, TipoDia.DISCIPLINA, null)).as("como en su primer dia").isEqualTo(30);
        assertThat(caminata.vigentesEn(30, TipoDia.DISCIPLINA)).containsExactly(DESDE_EL_DIA_30);
        assertThat(caminata.necesitaSaberSiYaCorrio(25, TipoDia.DISCIPLINA)).as("no hace falta leer registros")
                .isFalse();
    }

    @Test
    @DisplayName("D-216: el mismo horario en un habito del catalogo sigue esperando su dia si nunca corrio")
    void elMismoHorarioEnElCatalogoSigueEsperando() {
        HorariosDelHabito delCatalogo = HorariosDelHabito.de(delCatalogo(HABITO), List.of(DESDE_EL_DIA_30));

        assertThat(delCatalogo.diasParaArrancar(25, null)).isEqualTo(5);
        assertThat(delCatalogo.diaEfectivo(25, TipoDia.DISCIPLINA, null)).isEqualTo(25);
        assertThat(delCatalogo.necesitaSaberSiYaCorrio(25, TipoDia.DISCIPLINA)).isTrue();
    }

    @Test
    @DisplayName("D-216: crearlo prueba solo su PRIMER dia; un tramo posterior que nunca corrio sigue esperando")
    void crearloPruebaSoloSuPrimerDia() {
        HorarioHabito primero = horario(1, 10, TipoDia.TODOS, 6);
        HorarioHabito segundo = horario(20, 90, TipoDia.TODOS, 8);
        HorariosDelHabito horarios = HorariosDelHabito.de(personal(), List.of(primero, segundo));

        assertThat(horarios.diaEfectivo(15, TipoDia.DISCIPLINA, null)).isEqualTo(15);
        assertThat(horarios.necesitaSaberSiYaCorrio(15, TipoDia.DISCIPLINA)).isTrue();
        assertThat(horarios.diaEfectivo(15, TipoDia.DISCIPLINA, 21)).as("con registros, D-200 de siempre")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("D-216: porHabito arma uno por habito, con o sin horarios, y reconoce el personal")
    void porHabitoArmaUnoPorHabito() {
        Habito caminata = personal();
        Habito audioterapia = delCatalogo(HabitoId.of(UUID.randomUUID()));
        Habito sinHorario = delCatalogo(HabitoId.of(UUID.randomUUID()));

        Map<HabitoId, HorariosDelHabito> porHabito = HorariosDelHabito.porHabito(
                List.of(caminata, audioterapia, sinHorario),
                List.of(DESDE_EL_DIA_30, horarioDe(audioterapia.id(), 30, null, TipoDia.TODOS, 7)));

        assertThat(porHabito).containsOnlyKeys(caminata.id(), audioterapia.id(), sinHorario.id());
        assertThat(porHabito.get(caminata.id()).diasParaArrancar(25, null)).isZero();
        assertThat(porHabito.get(audioterapia.id()).diasParaArrancar(25, null)).isEqualTo(5);
        assertThat(porHabito.get(sinHorario.id()).primerDia()).isEmpty();
    }
}
