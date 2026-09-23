package com.renaser.os.rag.infrastructure.adapter.out.horarios;

import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementa {@link AjustarHorariosPort} delegando en el contrato publico de {@code habits}
 * (D-41). Traduccion y nada mas: las excepciones del negocio pasan tal cual, para que el
 * {@code AccionConfirmable} que llama las convierta en un {@code Fallo} legible. Mismo patron que
 * {@link ConsultarHorariosAdapter}.
 */
@Component
class AjustarHorariosAdapter implements AjustarHorariosPort {

    private final AjustarHorarioHabitoUseCase ajustarHorario;

    AjustarHorariosAdapter(AjustarHorarioHabitoUseCase ajustarHorario) {
        this.ajustarHorario = ajustarHorario;
    }

    @Override
    public HorarioCambiado cambiarHorario(UserId participanteId, CambioDeHorario cambio) {
        AjustarHorarioHabitoUseCase.CambioDeHorarioAplicado aplicado = ajustarHorario.cambiarHorario(
                new AjustarHorarioHabitoUseCase.CambioDeHorario(participanteId, cambio.habitoId(),
                        cambio.horaInicio(), cambio.horaLimite(), cambio.fecha()));
        return new HorarioCambiado(aplicado.horaInicio(), aplicado.horaLimite(), aplicado.rigeDesde(),
                aplicado.cambiosRestantes(), aplicado.cambiosLimite(), aplicado.semanaDeAcomodoLibre());
    }

    @Override
    public void cambiarEstadoDelDia(UserId participanteId, UUID habitoId, LocalDate fecha, boolean activo) {
        ajustarHorario.cambiarEstadoDelDia(participanteId,
                new AjustarHorarioHabitoUseCase.EstadoDelDia(habitoId, fecha, activo));
    }

    @Override
    public void fijarDiaDeLaSemana(UserId participanteId, HorarioSemanal horario) {
        ajustarHorario.fijarDiaDeLaSemana(new AjustarHorarioHabitoUseCase.HorarioDeDiaDeLaSemana(participanteId,
                horario.habitoId(), horario.diaSemana(), horario.horaInicio(), horario.horaLimite()));
    }

    @Override
    public void apagarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana) {
        ajustarHorario.apagarDiaDeLaSemana(participanteId, habitoId, diaSemana);
    }

    @Override
    public void quitarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana) {
        ajustarHorario.quitarDiaDeLaSemana(participanteId, habitoId, diaSemana);
    }
}
