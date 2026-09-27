package com.renaser.os.rocks.domain.model.rocasemanal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-203: la semana de programa es la semana CALENDARIO (lunes a domingo), contada desde la que contiene el
 * primer día efectivo; la 13 suma los días que caerían en una 14 y termina el día 90.
 *
 * <p>Reemplaza a las pruebas de D-192 (bloques de siete días del programa). Las de la tabla por día de
 * inicio y las del domingo fallan contra D-192: con inicio de martes a domingo, el día 7 ya es la semana 2
 * (empezó un lunes) y el 84 la 13; con D-192 eran la 1 y la 12.
 */
class SemanaProgramaTest {

    /** Lunes 2026-09-07; los seis días siguientes cubren martes a domingo. */
    private static final LocalDate UN_LUNES = LocalDate.of(2026, 9, 7);

    private static LocalDate inicioEn(DayOfWeek dia) {
        return UN_LUNES.plusDays(dia.getValue() - 1L);
    }

    @ParameterizedTest(name = "inicio {0}, dia {1} -> semana {2}")
    @CsvSource({
            "MONDAY, 1, 1", "MONDAY, 7, 1", "MONDAY, 8, 2", "MONDAY, 84, 12", "MONDAY, 85, 13", "MONDAY, 90, 13",
            "TUESDAY, 1, 1", "TUESDAY, 7, 2", "TUESDAY, 8, 2", "TUESDAY, 84, 13", "TUESDAY, 85, 13", "TUESDAY, 90, 13",
            "WEDNESDAY, 1, 1", "WEDNESDAY, 7, 2", "WEDNESDAY, 8, 2", "WEDNESDAY, 84, 13", "WEDNESDAY, 85, 13",
            "WEDNESDAY, 90, 13",
            "THURSDAY, 1, 1", "THURSDAY, 7, 2", "THURSDAY, 8, 2", "THURSDAY, 84, 13", "THURSDAY, 85, 13",
            "THURSDAY, 90, 13",
            "FRIDAY, 1, 1", "FRIDAY, 7, 2", "FRIDAY, 8, 2", "FRIDAY, 84, 13", "FRIDAY, 85, 13", "FRIDAY, 90, 13",
            "SATURDAY, 1, 1", "SATURDAY, 7, 2", "SATURDAY, 8, 2", "SATURDAY, 84, 13", "SATURDAY, 85, 13",
            "SATURDAY, 90, 13",
            "SUNDAY, 1, 1", "SUNDAY, 7, 2", "SUNDAY, 8, 2", "SUNDAY, 84, 13", "SUNDAY, 85, 13", "SUNDAY, 90, 13"
    })
    @DisplayName("inicio en cada dia de la semana: la semana es la de calendario y nunca la 14")
    void laSemanaEsLaDeCalendario(DayOfWeek diaDeInicio, int dia, int semanaEsperada) {
        LocalDate inicio = inicioEn(diaDeInicio);
        LocalDate fecha = inicio.plusDays(dia - 1L);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio);

        assertThat(semanas.numeroSemanaParaFecha(fecha)).isEqualTo(semanaEsperada);
        SemanaPrograma.LimitesSemana limites = semanas.limites(semanaEsperada);
        assertThat(fecha).isBetween(limites.inicio(), limites.fin());
    }

    @ParameterizedTest(name = "inicio {0}")
    @EnumSource(DayOfWeek.class)
    @DisplayName("las 13 semanas cubren los 90 dias sin huecos: la 1 corta, de la 2 a la 12 lunes a domingo, la 13 hasta el 90")
    void lasTreceSemanasCubrenLosNoventaDias(DayOfWeek diaDeInicio) {
        LocalDate inicio = inicioEn(diaDeInicio);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio);

        for (int dia = 1; dia <= 90; dia++) {
            assertThat(semanas.numeroSemanaParaFecha(inicio.plusDays(dia - 1L))).isBetween(1, 13);
        }
        assertThat(semanas.limites(1).inicio()).isEqualTo(inicio);
        assertThat(semanas.limites(1).fin().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        for (int semana = 2; semana <= 13; semana++) {
            assertThat(semanas.limites(semana).inicio()).isEqualTo(semanas.limites(semana - 1).fin().plusDays(1));
            assertThat(semanas.limites(semana).inicio().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }
        for (int semana = 2; semana <= 12; semana++) {
            assertThat(semanas.limites(semana).fin().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        }
        assertThat(semanas.limites(13).fin()).isEqualTo(inicio.plusDays(89)).isEqualTo(semanas.finDelPrograma());
        // La 13 dura de 6 (inicio lunes) a 12 dias (inicio domingo): suma los de la que seria la 14.
        assertThat(diasDe(semanas.limites(13))).isEqualTo(5 + diaDeInicio.getValue());
    }

    private static long diasDe(SemanaPrograma.LimitesSemana limites) {
        return ChronoUnit.DAYS.between(limites.inicio(), limites.fin()) + 1;
    }

    /**
     * Punto 4 de D-203, a nivel de calendario: sin ajuste el ancla es {@code fecha_inicio}, y para cada
     * fecha la semana tiene que ser la que dio producción ({@link CuentaDeSemanasDeProduccion}) cuando
     * aquella daba 1 a 13; cuando daba 14 o más (nunca se pudo guardar), ahora es la 13. Los límites de las
     * semanas 1 a 12 son los mismos, y la 13 empieza el mismo lunes.
     */
    @Test
    @DisplayName("sin ajuste, cada fecha cae en la misma semana que en produccion (y lo que alli era 14+, en la 13)")
    void sinAjusteCoincideConProduccion() {
        LocalDate primerInicio = LocalDate.of(2025, 12, 1);
        for (int desplazamiento = 0; desplazamiento < 400; desplazamiento++) {
            LocalDate inicio = primerInicio.plusDays(desplazamiento);
            SemanaPrograma semanas = SemanaPrograma.desde(inicio);
            for (int offset = -14; offset <= 120; offset++) {
                LocalDate fecha = inicio.plusDays(offset);
                int enProduccion = CuentaDeSemanasDeProduccion.numeroSemanaParaFecha(inicio, fecha);
                assertThat(semanas.numeroSemanaParaFecha(fecha)).as("inicio %s, fecha %s", inicio, fecha)
                        .isEqualTo(Math.min(enProduccion, SemanaPrograma.ULTIMA_SEMANA));
            }
            for (int semana = 1; semana <= 12; semana++) {
                assertThat(semanas.limites(semana)).as("inicio %s, semana %d", inicio, semana)
                        .isEqualTo(CuentaDeSemanasDeProduccion.limites(inicio, semana));
            }
            assertThat(semanas.limites(13).inicio()).isEqualTo(CuentaDeSemanasDeProduccion.limites(inicio, 13).inicio());
            assertThat(semanas.finDelPrograma()).isEqualTo(CuentaDeSemanasDeProduccion.finDelPrograma(inicio));
        }
    }

    /**
     * El {@code +1} del Domingo Ritual, igual que en producción, con dos diferencias pedidas por el dueño:
     * antes del día 1 se planifica la 1 (producción pedía la 2 el domingo previo) y nunca se pide la 14.
     * Inicio jueves 2026-09-10: la semana 1 va del jueves 10 al domingo 13; el día 90 es el martes 8 de
     * diciembre y la 13 va del lunes 30 de noviembre a ese martes.
     */
    @ParameterizedTest(name = "{0} -> planifica la semana {1}")
    @CsvSource({
            "2026-09-06, 1", // domingo antes del dia 1: la semana que empieza el lunes es la 1
            "2026-09-09, 1", // miercoles, dia 0
            "2026-09-10, 1", // jueves, dia 1
            "2026-09-12, 1", // sabado, dia 3
            "2026-09-13, 2", // domingo, dia 4: el Domingo Ritual prepara la semana 2
            "2026-09-14, 2", // lunes, dia 5
            "2026-09-20, 3", // domingo, dia 11
            "2026-11-29, 13", // domingo de la semana 12 (dia 81): prepara la 13
            "2026-11-30, 13", // lunes de la 13 (dia 82)
            "2026-12-06, 13", // domingo dentro de la 13 (dia 88): no hay 14
            "2026-12-08, 13", // martes, dia 90
            "2026-12-13, 13" // domingo, ya graduado
    })
    @DisplayName("semanaAPlanificar: el domingo prepara la siguiente; antes del dia 1 la 1; nunca la 14")
    void semanaAPlanificar(LocalDate hoy, int esperada) {
        SemanaPrograma semanas = SemanaPrograma.desde(LocalDate.of(2026, 9, 10));

        assertThat(semanas.semanaAPlanificar(hoy)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("inicio en miercoles: la semana 1 va de miercoles a domingo y la 13 de lunes a lunes (8 dias)")
    void inicioEnMiercoles() {
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        SemanaPrograma semanas = SemanaPrograma.desde(miercoles);

        assertThat(semanas.limites(1)).isEqualTo(new SemanaPrograma.LimitesSemana(miercoles, LocalDate.of(2026, 9, 13)));
        assertThat(semanas.limites(2)).isEqualTo(
                new SemanaPrograma.LimitesSemana(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)));
        assertThat(semanas.limites(13)).isEqualTo(
                new SemanaPrograma.LimitesSemana(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 7)));
        assertThat(semanas.finDelPrograma()).isEqualTo(LocalDate.of(2026, 12, 7));
    }

    @Test
    @DisplayName("inicio en lunes: la 13 va del lunes del dia 85 al sabado del dia 90 (el domingo 91 ya no es programa)")
    void inicioEnLunes() {
        SemanaPrograma semanas = SemanaPrograma.desde(UN_LUNES);

        assertThat(semanas.limites(13)).isEqualTo(
                new SemanaPrograma.LimitesSemana(UN_LUNES.plusDays(84), UN_LUNES.plusDays(89)));
        assertThat(semanas.limites(13).fin().getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
    }

    @Test
    @DisplayName("antes del inicio todo es la semana 1, y pasado el dia 90 todo es la 13")
    void antesDelInicioYDespuesDelFin() {
        LocalDate inicio = LocalDate.of(2026, 9, 10);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio);

        assertThat(semanas.numeroSemanaParaFecha(inicio.minusDays(10))).isEqualTo(1);
        assertThat(semanas.numeroSemanaParaFecha(inicio.plusDays(120))).isEqualTo(13);
        assertThat(semanas.limites(14)).isEqualTo(semanas.limites(13));
        assertThat(semanas.limites(0)).isEqualTo(semanas.limites(1));
    }
}
