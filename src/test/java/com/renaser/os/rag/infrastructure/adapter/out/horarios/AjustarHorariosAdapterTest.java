package com.renaser.os.rag.infrastructure.adapter.out.horarios;

import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.CambioDeHorarioAplicado;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.EstadoDelDia;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.HorarioDeDiaDeLaSemana;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.CambioDeHorario;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioCambiado;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioSemanal;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Traduccion campo a campo al contrato de {@code habits}; los rechazos del negocio pasan tal cual. */
class AjustarHorariosAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID HABITO = UUID.randomUUID();

    private final AjustarHorarioHabitoUseCase habits = mock(AjustarHorarioHabitoUseCase.class);
    private final AjustarHorariosAdapter adapter = new AjustarHorariosAdapter(habits);

    @Test
    void traduceElCambioDeHorarioYSuResultado() {
        LocalDate viernes = LocalDate.of(2026, 9, 11);
        when(habits.cambiarHorario(new AjustarHorarioHabitoUseCase.CambioDeHorario(APRENDIZ, HABITO,
                LocalTime.of(6, 30), LocalTime.of(7, 30), viernes))).thenReturn(new CambioDeHorarioAplicado(
                LocalTime.of(6, 30), LocalTime.of(7, 30), viernes, 2, 1, 3, false));

        HorarioCambiado cambiado = adapter.cambiarHorario(APRENDIZ, new CambioDeHorario(HABITO, LocalTime.of(6, 30),
                LocalTime.of(7, 30), viernes));

        assertThat(cambiado).isEqualTo(new HorarioCambiado(LocalTime.of(6, 30), LocalTime.of(7, 30), viernes, 1, 3,
                false));
    }

    @Test
    void delegaDiaYDiaDeSemana() {
        LocalDate viernes = LocalDate.of(2026, 9, 11);

        adapter.cambiarEstadoDelDia(APRENDIZ, HABITO, viernes, false);
        adapter.fijarDiaDeLaSemana(APRENDIZ, new HorarioSemanal(HABITO, DayOfWeek.MONDAY, LocalTime.of(5, 0), null));
        adapter.apagarDiaDeLaSemana(APRENDIZ, HABITO, DayOfWeek.TUESDAY);
        adapter.quitarDiaDeLaSemana(APRENDIZ, HABITO, DayOfWeek.WEDNESDAY);

        verify(habits).cambiarEstadoDelDia(APRENDIZ, new EstadoDelDia(HABITO, viernes, false));
        verify(habits).fijarDiaDeLaSemana(new HorarioDeDiaDeLaSemana(APRENDIZ, HABITO, DayOfWeek.MONDAY,
                LocalTime.of(5, 0), null));
        verify(habits).apagarDiaDeLaSemana(APRENDIZ, HABITO, DayOfWeek.TUESDAY);
        verify(habits).quitarDiaDeLaSemana(APRENDIZ, HABITO, DayOfWeek.WEDNESDAY);
    }

    @Test
    void elRechazoDelNegocioPasaTalCual() {
        when(habits.cambiarHorario(any())).thenThrow(new IllegalStateException("sin cupo"));

        assertThatThrownBy(() -> adapter.cambiarHorario(APRENDIZ, new CambioDeHorario(HABITO, LocalTime.of(6, 30),
                null, null))).isInstanceOf(IllegalStateException.class);
    }
}
