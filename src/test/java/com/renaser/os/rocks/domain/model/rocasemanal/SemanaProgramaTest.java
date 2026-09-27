package com.renaser.os.rocks.domain.model.rocasemanal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-192 (E-320): la semana de programa es {@code ceil(dia / 7)}, de 1 a 13, y sigue al dia cuando se
 * ajusta. Reemplaza a las pruebas de la cuenta calendario lunes-domingo, incluidas las dos de
 * caracterizacion que fijaban la «semana 14».
 */
class SemanaProgramaTest {

    /** Lunes 2026-09-07; los seis dias siguientes cubren martes a domingo. */
    private static final LocalDate UN_LUNES = LocalDate.of(2026, 9, 7);

    static Stream<Arguments> inicioEnCadaDiaDeLaSemanaPorDia() {
        int[][] diaYSemana = {{1, 1}, {7, 1}, {8, 2}, {84, 12}, {85, 13}, {90, 13}};
        return Stream.iterate(0, i -> i + 1).limit(7)
                .flatMap(desplazamiento -> Stream.of(diaYSemana)
                        .map(par -> Arguments.of(UN_LUNES.plusDays(desplazamiento), par[0], par[1])));
    }

    @ParameterizedTest(name = "inicio {0}, dia {1} -> semana {2}")
    @MethodSource("inicioEnCadaDiaDeLaSemanaPorDia")
    @DisplayName("inicio en cualquier dia de la semana: la semana sale del dia de programa")
    void laSemanaSaleDelDiaDePrograma(LocalDate inicio, int dia, int semanaEsperada) {
        LocalDate hoy = inicio.plusDays(dia - 1L);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, dia, hoy);

        assertThat(semanas.numeroSemanaParaFecha(hoy)).isEqualTo(semanaEsperada);
        assertThat(SemanaPrograma.numeroDeDia(dia)).isEqualTo(semanaEsperada);
    }

    @ParameterizedTest(name = "inicio en {0}")
    @MethodSource("inicios")
    @DisplayName("los 90 dias caen en las semanas 1 a 13, nunca en la 14, empiece el dia que empiece")
    void nuncaHaySemanaCatorce(DayOfWeek diaDeInicio) {
        LocalDate inicio = UN_LUNES.plusDays(diaDeInicio.getValue() - 1L);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 1, inicio);

        for (int dia = 1; dia <= 90; dia++) {
            assertThat(semanas.numeroSemanaParaFecha(inicio.plusDays(dia - 1L))).isBetween(1, 13);
        }
        assertThat(semanas.numeroSemanaParaFecha(inicio.plusDays(89))).isEqualTo(13);
        assertThat(semanas.limites(13)).isEqualTo(
                new SemanaPrograma.LimitesSemana(inicio.plusDays(84), inicio.plusDays(89)));
        assertThat(semanas.finDelPrograma()).isEqualTo(inicio.plusDays(89));
    }

    static Stream<DayOfWeek> inicios() {
        return Stream.of(DayOfWeek.values());
    }

    @Test
    @DisplayName("limites: siete dias del programa, empezando el dia de inicio, sea el dia que sea")
    void limitesSonBloquesDeSieteDias() {
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        SemanaPrograma semanas = SemanaPrograma.desde(miercoles, 1, miercoles);

        assertThat(semanas.limites(1)).isEqualTo(new SemanaPrograma.LimitesSemana(miercoles, miercoles.plusDays(6)));
        assertThat(semanas.limites(2)).isEqualTo(
                new SemanaPrograma.LimitesSemana(miercoles.plusDays(7), miercoles.plusDays(13)));
    }

    @Test
    @DisplayName("empezar un lunes sin ajuste da lo mismo que la cuenta vieja lunes-domingo")
    void inicioEnLunesCoincideConLaCuentaVieja() {
        SemanaPrograma semanas = SemanaPrograma.desde(UN_LUNES, 1, UN_LUNES);

        assertThat(semanas.limites(1)).isEqualTo(new SemanaPrograma.LimitesSemana(UN_LUNES, UN_LUNES.plusDays(6)));
        assertThat(semanas.limites(1).fin().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(semanas.limites(5).inicio().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("adelantar el dia: la semana sigue al dia, no al calendario (dia 35 -> semana 5)")
    void trasAdelantarLaSemanaSigueAlDia() {
        LocalDate inicio = LocalDate.of(2026, 9, 1);
        LocalDate hoy = inicio.plusDays(9); // dia 10 de calendario, adelantado al 35
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 35, hoy);

        assertThat(semanas.numeroSemanaParaFecha(hoy)).isEqualTo(5);
        assertThat(semanas.limites(5)).isEqualTo(new SemanaPrograma.LimitesSemana(hoy.minusDays(6), hoy));
        assertThat(semanas.finDelPrograma()).isEqualTo(hoy.plusDays(55));
    }

    @Test
    @DisplayName("retroceder el dia: el final del programa se corre y sigue cayendo en la semana 13")
    void trasRetrocederElFinalSigueEnLaSemanaTrece() {
        LocalDate inicio = LocalDate.of(2026, 9, 7);
        LocalDate hoy = inicio.plusDays(39); // dia 40 de calendario, retrocedido al 34
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 34, hoy);

        assertThat(semanas.numeroSemanaParaFecha(hoy)).isEqualTo(5);
        assertThat(semanas.primerDia()).isEqualTo(inicio.plusDays(6));
        assertThat(semanas.finDelPrograma()).isEqualTo(inicio.plusDays(95));
        assertThat(semanas.numeroSemanaParaFecha(semanas.finDelPrograma())).isEqualTo(13);
    }

    @Test
    @DisplayName("antes del inicio: el ancla es fecha_inicio y todo lo anterior es semana 1")
    void antesDelInicio() {
        LocalDate inicio = LocalDate.of(2026, 9, 10);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 0, inicio.minusDays(3));

        assertThat(semanas.primerDia()).isEqualTo(inicio);
        assertThat(semanas.numeroSemanaParaFecha(inicio.minusDays(3))).isEqualTo(1);
        assertThat(semanas.semanaAPlanificar(inicio.minusDays(1))).isEqualTo(1);
    }

    @ParameterizedTest(name = "dia {0} -> planifica la semana {1}")
    @CsvSource({"1, 1", "6, 1", "7, 2", "8, 2", "14, 3", "83, 12", "84, 13", "85, 13", "90, 13"})
    @DisplayName("semanaAPlanificar: el ultimo dia de cada semana prepara la siguiente, nunca la 14")
    void semanaAPlanificar(int dia, int esperada) {
        LocalDate inicio = LocalDate.of(2026, 9, 10); // jueves: el corte ya no es el domingo
        LocalDate hoy = inicio.plusDays(dia - 1L);

        assertThat(SemanaPrograma.desde(inicio, dia, hoy).semanaAPlanificar(hoy)).isEqualTo(esperada);
    }
}
