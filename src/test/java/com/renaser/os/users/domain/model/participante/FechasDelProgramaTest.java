package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las fechas del día 1 y del día 90 que usa el semáforo (D-168) tienen que coincidir con el día que
 * deriva el propio reloj del programa, también con los ajustes del administrador en los dos sentidos.
 */
class FechasDelProgramaTest {

    /** Jueves 10 de septiembre, 10:00 en Lima. */
    private static final FixedClock INICIO = FixedClock.at(Instant.parse("2026-09-10T15:00:00Z"));
    /** Diez días después: el día 11 del programa. */
    private static final FixedClock DIA_ONCE = FixedClock.at(Instant.parse("2026-09-20T15:00:00Z"));

    private static ParticipacionPrograma programaDesdeElDiez() {
        return ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), INICIO);
    }

    @Test
    void sinAjusteElDiaUnoEsElInicioYElNoventaOchentaYNueveDiasDespues() {
        ParticipacionPrograma programa = programaDesdeElDiez();

        assertThat(programa.primeraFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(programa.ultimaFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(programa.diaProgramaDerivado(programa.ultimaFechaDelPrograma())).isEqualTo(90);
    }

    /** Se lo retrocedió (viajó): su día 1 se corre hacia adelante, y también su día 90. */
    @Test
    void siSeLoRetrocedeElDiaUnoYElNoventaSeCorren() {
        ParticipacionPrograma programa = programaDesdeElDiez();
        programa.fijarDia(5, DIA_ONCE);

        assertThat(programa.primeraFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(programa.diaProgramaDerivado(LocalDate.of(2026, 9, 16))).isEqualTo(1);
        assertThat(programa.ultimaFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 12, 14));
        assertThat(programa.diaProgramaDerivado(LocalDate.of(2026, 12, 14))).isEqualTo(90);
    }

    /** Se lo adelantó: los días salteados nunca existieron; el primer día contado es el inicio. */
    @Test
    void siSeLoAdelantaElPrimerDiaContadoEsElInicio() {
        ParticipacionPrograma programa = programaDesdeElDiez();
        programa.fijarDia(15, DIA_ONCE);

        assertThat(programa.primeraFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(programa.diaProgramaDerivado(LocalDate.of(2026, 9, 10))).isEqualTo(5);
        assertThat(programa.ultimaFechaDelPrograma()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(programa.diaProgramaDerivado(LocalDate.of(2026, 12, 4))).isEqualTo(90);
    }

    @Test
    void sinProgramaActivadoNoHayFechas() {
        ParticipacionPrograma pendiente = ParticipacionPrograma.inscribirTraineeAprobado(UserId.of(UUID.randomUUID()), INICIO);

        assertThat(pendiente.primeraFechaDelPrograma()).isNull();
        assertThat(pendiente.ultimaFechaDelPrograma()).isNull();
    }
}
