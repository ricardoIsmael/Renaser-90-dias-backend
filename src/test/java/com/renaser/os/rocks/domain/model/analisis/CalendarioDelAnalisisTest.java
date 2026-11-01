package com.renaser.os.rocks.domain.model.analisis;

import com.renaser.os.rocks.domain.model.analisis.CalendarioDelAnalisis.Periodo;
import com.renaser.os.rocks.domain.model.analisis.CalendarioDelAnalisis.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las semanas y los meses del programa, y lo que sigue después de la semana 13 (spec 031, 031-6 A y 031-8 B). Aritmética pura sobre el
 * Día 1: sin zona, sin reloj, sin Spring. El lunes 17 de agosto de 2026 es lunes.
 */
class CalendarioDelAnalisisTest {

    private static final LocalDate LUNES_DIA_UNO = LocalDate.of(2026, 8, 17);
    private static final LocalDate MIERCOLES_DIA_UNO = LocalDate.of(2026, 8, 19);

    private final CalendarioDelAnalisis desdeUnLunes = CalendarioDelAnalisis.desde(LUNES_DIA_UNO);
    private final CalendarioDelAnalisis desdeUnMiercoles = CalendarioDelAnalisis.desde(MIERCOLES_DIA_UNO);

    @Test
    @DisplayName("las semanas 1 a 13 son las de Plan: la 1 empieza el Día 1 y la 13 llega al día 90")
    void lasSemanasDePlan() {
        assertThat(desdeUnLunes.semana(1)).isEqualTo(new Periodo(Tipo.SEMANA, 1, LUNES_DIA_UNO, LocalDate.of(2026, 8, 23)));
        assertThat(desdeUnLunes.semana(13)).isEqualTo(new Periodo(Tipo.SEMANA, 13, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 14)));
        assertThat(desdeUnMiercoles.semana(1)).isEqualTo(new Periodo(Tipo.SEMANA, 1, MIERCOLES_DIA_UNO, LocalDate.of(2026, 8, 23)));
        assertThat(desdeUnMiercoles.semana(13).fin()).isEqualTo(MIERCOLES_DIA_UNO.plusDays(89));
    }

    @Test
    @DisplayName("031-8 B: después de la semana 13 el calendario sigue sin tope: la 14 empieza el día siguiente y llega a su domingo")
    void despuesDelDiaNoventa() {
        assertThat(desdeUnLunes.semana(14)).isEqualTo(new Periodo(Tipo.SEMANA, 14, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));
        assertThat(desdeUnLunes.semana(15)).isEqualTo(new Periodo(Tipo.SEMANA, 15, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)));
        assertThat(desdeUnMiercoles.semana(14)).isEqualTo(new Periodo(Tipo.SEMANA, 14, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 22)));
        assertThat(desdeUnMiercoles.semana(15)).isEqualTo(new Periodo(Tipo.SEMANA, 15, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29)));
        assertThat(desdeUnMiercoles.semana(60).inicio().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("la semana que contiene una fecha, antes y después del día 90; antes del Día 1 es la 1")
    void numeroDeSemanaDeUnaFecha() {
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 8, 1))).isEqualTo(1);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 8, 19))).isEqualTo(1);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 8, 24))).isEqualTo(2);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 11, 16))).isEqualTo(13);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 11, 17))).isEqualTo(14);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 11, 22))).isEqualTo(14);
        assertThat(desdeUnMiercoles.numeroDeSemanaDe(LocalDate.of(2026, 11, 23))).isEqualTo(15);
    }

    @Test
    @DisplayName("031-6 A: los meses son bloques de cuatro semanas con la 13 en el mes 3 (1–4, 5–8, 9–13) y después siguen de a cuatro")
    void losMeses() {
        assertThat(List.of(1, 4, 5, 8, 9, 13, 14, 17, 18, 21, 22)).extracting(desdeUnLunes::numeroDeMesDeLaSemana)
                .containsExactly(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6);
        assertThat(desdeUnLunes.mes(1)).isEqualTo(new Periodo(Tipo.MES, 1, LUNES_DIA_UNO, LocalDate.of(2026, 9, 13)));
        assertThat(desdeUnLunes.mes(3)).isEqualTo(new Periodo(Tipo.MES, 3, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 14)));
        assertThat(desdeUnLunes.mes(4)).isEqualTo(new Periodo(Tipo.MES, 4, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 6)));
        assertThat(desdeUnLunes.semanasDelMes(3)).extracting(Periodo::numero).containsExactly(9, 10, 11, 12, 13);
        assertThat(desdeUnLunes.semanasDelMes(2)).extracting(Periodo::numero).containsExactly(5, 6, 7, 8);
    }

    @Test
    @DisplayName("el mes y la semana de una fecha concuerdan, y un mes empieza donde empieza su primera semana")
    void mesDeUnaFecha() {
        assertThat(desdeUnLunes.mesDe(LocalDate.of(2026, 11, 14)).numero()).isEqualTo(3);
        assertThat(desdeUnLunes.mesDe(LocalDate.of(2026, 11, 15)).numero()).isEqualTo(4);
        assertThat(desdeUnLunes.semanaDe(LocalDate.of(2026, 11, 14)).numero()).isEqualTo(13);
        for (int mes = 1; mes <= 8; mes++) {
            assertThat(desdeUnMiercoles.mes(mes).inicio()).isEqualTo(desdeUnMiercoles.semanasDelMes(mes).get(0).inicio());
            assertThat(desdeUnMiercoles.mes(mes).fin()).isEqualTo(desdeUnMiercoles.semanasDelMes(mes).getLast().fin());
        }
    }

    @Test
    @DisplayName("los periodos no se solapan ni dejan días sueltos: cada día cae en una sola semana")
    void sinHuecosNiSolapes() {
        for (CalendarioDelAnalisis calendario : List.of(desdeUnLunes, desdeUnMiercoles)) {
            LocalDate esperado = calendario.semana(1).inicio();
            for (int semana = 1; semana <= 70; semana++) {
                Periodo periodo = calendario.semana(semana);
                assertThat(periodo.inicio()).as("inicio de la semana %d", semana).isEqualTo(esperado);
                assertThat(calendario.numeroDeSemanaDe(periodo.inicio())).isEqualTo(semana);
                assertThat(calendario.numeroDeSemanaDe(periodo.fin())).isEqualTo(semana);
                esperado = periodo.fin().plusDays(1);
            }
        }
    }

    @Test
    @DisplayName("lo que cerró: una semana cierra cuando pasa su último día, y se mira solo la ventana de puesta al día")
    void cerradosEnLaVentana() {
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2026, 11, 3), 7)).extracting(Periodo::tipo, Periodo::numero)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Tipo.SEMANA, 11));
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2026, 10, 12), 7)).extracting(Periodo::tipo, Periodo::numero)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Tipo.SEMANA, 8), org.assertj.core.groups.Tuple.tuple(Tipo.MES, 2));
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2026, 8, 20), 7)).isEmpty();
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2026, 8, 24), 7)).extracting(Periodo::numero).containsExactly(1);
    }

    @Test
    @DisplayName("031-8 B: terminado el programa se siguen cerrando semanas y meses")
    void siguenCerrandoDespuesDelNoventa() {
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2026, 11, 17), 7)).extracting(Periodo::tipo, Periodo::numero)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Tipo.SEMANA, 13), org.assertj.core.groups.Tuple.tuple(Tipo.SEMANA, 14),
                        org.assertj.core.groups.Tuple.tuple(Tipo.MES, 3));
        assertThat(desdeUnLunes.cerrados(LocalDate.of(2027, 8, 17), 7)).isNotEmpty().allSatisfy(periodo -> assertThat(periodo.cerroA(LocalDate.of(2027, 8, 17))).isTrue());
    }

    @Test
    @DisplayName("el número de semana o de mes debe empezar en 1")
    void numerosInvalidos() {
        assertThatThrownBy(() -> desdeUnLunes.semana(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> desdeUnLunes.mes(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
