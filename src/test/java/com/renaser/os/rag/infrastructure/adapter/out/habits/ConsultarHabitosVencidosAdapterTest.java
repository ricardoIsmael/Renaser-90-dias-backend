package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort.HabitoVencido;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-177: vencido lo decide {@code habits}; lo opcional de ese dia no cuenta. */
class ConsultarHabitosVencidosAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 21);

    private final ObligacionesHistoricasFinder finder = mock(ObligacionesHistoricasFinder.class);
    private final ConsultarHabitosVencidosAdapter adapter = new ConsultarHabitosVencidosAdapter(finder);

    @Test
    @DisplayName("fallido y expirado cuentan; completado, pendiente y lo opcional no")
    void soloVencidosNoOpcionales() {
        when(finder.porParticipantesEntre(List.of(APRENDIZ), LUNES, LUNES.plusDays(2))).thenReturn(List.of(
                obligacion("Ducha fria", EstadoObligacion.FALLIDO, false),
                obligacion("Leer", EstadoObligacion.EXPIRADO, false),
                obligacion("Caminar", EstadoObligacion.COMPLETADO, false),
                obligacion("Meditar", EstadoObligacion.PENDIENTE, false),
                obligacion("Opcional", EstadoObligacion.EXPIRADO, true)));

        assertThat(adapter.vencidosEntre(APRENDIZ, LUNES, LUNES.plusDays(2))).containsExactly(
                new HabitoVencido(LUNES, "Ducha fria"), new HabitoVencido(LUNES, "Leer"));
    }

    @Test
    @DisplayName("un rango vacio (hasta antes de desde) no consulta")
    void rangoVacio() {
        assertThat(adapter.vencidosEntre(APRENDIZ, LUNES, LUNES.minusDays(1))).isEmpty();
        verifyNoInteractions(finder);
    }

    private static ObligacionHabito obligacion(String titulo, EstadoObligacion estado, boolean opcional) {
        return new ObligacionHabito(UUID.randomUUID(), APRENDIZ, LUNES, 21, titulo, estado, true, opcional);
    }
}
