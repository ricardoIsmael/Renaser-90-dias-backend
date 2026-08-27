package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * Implementa {@link CompletarClaseDiariaHabitoUseCase} delegando en {@link CompletarRegistroUseCase}
 * para el cálculo de puntos, ventana de entrega y evento de dominio — ese cálculo vive en un
 * solo lugar ({@code RegistroService}, ver javadoc de {@code PoliticaHabito}), esta clase solo
 * localiza el track de HOY del hábito {@code DAILY_CLASS} sin exponer su identidad al llamador.
 */
@Service
public class ClaseDiariaHabitoService implements CompletarClaseDiariaHabitoUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final Clock clock;

    public ClaseDiariaHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                     ConsultarProgresoParticipanteHabitsPort progresoPort,
                                     CompletarRegistroUseCase completarRegistroUseCase, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.clock = clock;
    }

    @Override
    public RegistroCompletado completarDeHoy(CompletarClaseDiariaHabitoCommand command) {
        UserId participanteId = command.participanteId();
        Habito habitoDailyClass = loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_DAILY_CLASS)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe en el catalogo un habito con claveSistema=" + CLAVE_SISTEMA_DAILY_CLASS));

        LocalDate hoy = fechaHoyEnZonaDe(requireProgresoNoSuspendido(participanteId).timezone());
        RegistroHabito registro = loadRegistroPort
                .porParticipanteHabitoYFecha(participanteId, habitoDailyClass.id(), hoy)
                .orElseThrow(() -> new NoSuchElementException("No hay Clase Diaria generada para hoy"));

        if (registro.estado() == EstadoRegistro.COMPLETADO) {
            return new RegistroCompletado(registro.id().value(), registro.puntosOtorgados());
        }

        RegistroHabito completado = completarRegistroUseCase.completar(
                new CompletarRegistroCommand(participanteId, registro.id(), command.resumen(), null));
        return new RegistroCompletado(completado.id().value(), completado.puntosOtorgados());
    }

    /**
     * Defensa propia: aunque hoy el unico llamador (`academy`) ya valida suspension antes de
     * llegar aca (resolviendo la Clase Diaria del dia), este puerto es publico
     * ({@code habits.api}) y no debe confiar en que todo futuro llamador repita ese chequeo —
     * mismo criterio que {@code RegistroService.requireProgreso}.
     */
    private ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits requireProgresoNoSuspendido(
            UserId participanteId) {
        var progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }

    /** Mismo criterio que {@code RachaService.fechaHoyDe}: reloj inyectado, nunca el del sistema. */
    private LocalDate fechaHoyEnZonaDe(String timezone) {
        return clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
    }
}
