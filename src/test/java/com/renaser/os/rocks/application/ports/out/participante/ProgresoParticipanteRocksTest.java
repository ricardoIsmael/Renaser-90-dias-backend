package com.renaser.os.rocks.application.ports.out.participante;

import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.domain.model.rocasemanal.CuentaDeSemanasDeProduccion;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-203: de qué fecha sale la semana de rocas de una persona ({@link ProgresoParticipanteRocks#primerDiaEfectivo})
 * con lo que da {@code users.api}: el Día 1 elegido ({@code null} sin activar, D-201), el día de hoy ya derivado
 * con el ajuste y acotado a 0..90, y, desde el día 90, la fecha real del día 90 que trae el adaptador.
 *
 * <p>El día se arma como lo arma {@code users} ({@code ConsultarResumenParticipacionPersistenceAdapter.diaVigente}
 * y {@code ParticipacionPrograma.diaProgramaDerivado}): antes de {@code fecha_inicio} manda el día guardado (0);
 * después, {@code acotar([0, 90], transcurridos − ajuste)}.
 */
class ProgresoParticipanteRocksTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Una persona que ya eligió su Día 1, con el día que da {@code users.api} y la fecha del día 90 desde el 90. */
    private static ProgresoParticipanteRocks conDiaUno(LocalDate diaUno, LocalDate hoy, int ajuste) {
        int dia = diaUno.isAfter(hoy) ? 0 : Math.clamp(ChronoUnit.DAYS.between(diaUno, hoy) + 1 - ajuste, 0, 90);
        LocalDate ultimaFecha = dia >= 90 ? diaUno.plusDays(89L + ajuste) : null;
        return new ProgresoParticipanteRocks(dia, diaUno, LIMA, RolParticipante.TRAINEE, false, true, ultimaFecha);
    }

    /**
     * El punto 4 de D-203: para quien NO tiene ajuste, en cualquier estado —antes del día 1, en curso, en el
     * día 90 o ya graduado—, el ancla es el Día 1 y cada fecha cae en la misma semana que en producción (y lo
     * que allí era 14 o más, en la 13). Con el ancla de D-192 ({@code hoy − (día − 1)}) fallaba para el
     * graduado: el ancla se corría un día por cada día que pasaba (E-339).
     */
    @Test
    @DisplayName("sin ajuste, antes del dia 1, en curso o graduado, la semana de cada fecha es la de produccion (E-339)")
    void sinAjusteLaNumeracionEsLaDeProduccion() {
        LocalDate primerInicio = LocalDate.of(2026, 5, 4);
        for (int desplazamiento = 0; desplazamiento < 14; desplazamiento++) {
            LocalDate inicio = primerInicio.plusDays(desplazamiento);
            for (int offsetDeHoy = -5; offsetDeHoy <= 130; offsetDeHoy += 3) {
                LocalDate hoy = inicio.plusDays(offsetDeHoy);
                verificarContraProduccion(conDiaUno(inicio, hoy, 0), inicio, hoy);
            }
        }
    }

    private static void verificarContraProduccion(ProgresoParticipanteRocks progreso, LocalDate inicio,
                                                  LocalDate hoy) {
        SemanaPrograma semanas = progreso.semanas(hoy).orElseThrow();
        assertThat(semanas.primerDia()).as("inicio %s, hoy %s, dia %d", inicio, hoy, progreso.diaPrograma())
                .isEqualTo(inicio);
        for (int offset = -7; offset <= 110; offset++) {
            LocalDate fecha = inicio.plusDays(offset);
            assertThat(semanas.numeroSemanaParaFecha(fecha)).as("inicio %s, hoy %s, fecha %s", inicio, hoy, fecha)
                    .isEqualTo(Math.min(CuentaDeSemanasDeProduccion.numeroSemanaParaFecha(inicio, fecha), 13));
        }
    }

    /**
     * La semana que se planifica hoy coincide con producción salvo en lo que el dueño pidió cambiar: el
     * domingo antes del día 1 (producción pedía la 2) y el +1 que llegaba a la 14.
     */
    @Test
    @DisplayName("sin ajuste, la semana que se planifica es la de produccion salvo el domingo previo al dia 1 y la 14")
    void sinAjusteSePlanificaLaMismaSemanaQueEnProduccion() {
        LocalDate primerInicio = LocalDate.of(2026, 5, 4);
        for (int desplazamiento = 0; desplazamiento < 14; desplazamiento++) {
            LocalDate inicio = primerInicio.plusDays(desplazamiento);
            for (int offsetDeHoy = -6; offsetDeHoy <= 100; offsetDeHoy++) {
                LocalDate hoy = inicio.plusDays(offsetDeHoy);
                int nueva = conDiaUno(inicio, hoy, 0).semanas(hoy).orElseThrow().semanaAPlanificar(hoy);
                int enProduccion = CuentaDeSemanasDeProduccion.semanaAPlanificar(inicio, hoy);
                int esperada = hoy.isBefore(inicio) ? 1 : Math.min(enProduccion, 13);
                assertThat(nueva).as("inicio %s, hoy %s", inicio, hoy).isEqualTo(esperada);
            }
        }
    }

    @ParameterizedTest(name = "ajuste {0}")
    @ValueSource(ints = {-25, -7, -1, 1, 3, 7, 20})
    @DisplayName("con ajuste, el primer dia efectivo es el Dia 1 + ajuste: en curso, el dia 90 y ya graduado")
    void conAjusteLaSemanaAcompanaAlDia(int ajuste) {
        LocalDate inicio = LocalDate.of(2026, 6, 3);
        // Desde el dia en que el ajuste deja a la persona en su dia 1 o mas (fijarDia acepta 1..89) hasta
        // cincuenta dias despues de graduarse.
        for (int offsetDeHoy = Math.max(0, ajuste); offsetDeHoy <= 140; offsetDeHoy++) {
            LocalDate hoy = inicio.plusDays(offsetDeHoy);
            ProgresoParticipanteRocks progreso = conDiaUno(inicio, hoy, ajuste);

            assertThat(progreso.primerDiaEfectivo(hoy)).as("hoy %s, dia %d", hoy, progreso.diaPrograma())
                    .contains(inicio.plusDays(ajuste));
        }
    }

    @Test
    @DisplayName("tras adelantar del 10 al 35, la semana de hoy es la de calendario del dia 35, no la del 10")
    void trasAdelantarElDia() {
        LocalDate inicio = LocalDate.of(2026, 9, 1); // martes
        LocalDate hoy = LocalDate.of(2026, 9, 10); // dia 10 de calendario
        ProgresoParticipanteRocks progreso = conDiaUno(inicio, hoy, -25);
        SemanaPrograma semanas = progreso.semanas(hoy).orElseThrow();

        assertThat(progreso.diaPrograma()).isEqualTo(35);
        assertThat(semanas.primerDia()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(semanas.numeroSemanaParaFecha(hoy)).isEqualTo(6);
        assertThat(CuentaDeSemanasDeProduccion.numeroSemanaParaFecha(inicio, hoy)).isEqualTo(2);
        assertThat(semanas.limites(6).inicio().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    /**
     * D-201: sin activar, {@code users.api} ya no manda la fecha provisional del alta como Día 1. Sin Día 1 no
     * hay semanas con fechas: el que lee decide (la semana es la 1, sin rango). Antes se anclaba en esa fecha
     * provisional (producción) o, si ya había pasado, en mañana (D-192).
     */
    @Test
    @DisplayName("sin Dia 1 elegido no hay semanas con fechas")
    void sinDiaUnoElegido() {
        ProgresoParticipanteRocks sinActivar = new ProgresoParticipanteRocks(0, null, LIMA, RolParticipante.TRAINEE,
                false, false);
        LocalDate hoy = LocalDate.of(2026, 9, 27);

        assertThat(sinActivar.primerDiaEfectivo(hoy)).isEmpty();
        assertThat(sinActivar.semanas(hoy)).isEmpty();
    }
}
