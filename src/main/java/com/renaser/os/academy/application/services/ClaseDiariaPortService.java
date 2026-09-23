package com.renaser.os.academy.application.services;

import com.renaser.os.academy.api.ClaseDiariaPort;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.CompletarClaseDiariaCommand;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.ClaseDiariaResolution;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.NoIniciado;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Proximamente;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * Implementa {@link ClaseDiariaPort} (2026-09-23). Fachada delgada, mismo patron que
 * {@code habits.application.services.PlanDeHabitosService}: traduce al contrato publico lo que
 * devuelven los casos de uso de la Clase Diaria, y nada mas. Sin {@code @Transactional}: la
 * entrega trae la suya.
 */
@Service
public class ClaseDiariaPortService implements ClaseDiariaPort {

    private final ConsultarClaseDiariaUseCase consultarUseCase;
    private final CompletarClaseDiariaUseCase completarUseCase;

    public ClaseDiariaPortService(ConsultarClaseDiariaUseCase consultarUseCase,
                                  CompletarClaseDiariaUseCase completarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.completarUseCase = completarUseCase;
    }

    @Override
    public ClaseDeHoy claseDeHoy(UserId actorId) {
        return aClaseDeHoy(consultarUseCase.claseDeHoy(actorId));
    }

    @Override
    public Entrega entregar(UserId actorId, String leccionId, String resumen) {
        ClaseDiariaCompletada completada = completarUseCase.completar(
                new CompletarClaseDiariaCommand(actorId, LeccionId.of(leccionId), resumen));
        return new Entrega(completada.leccionId().value(), completada.puntosOtorgados());
    }

    static ClaseDeHoy aClaseDeHoy(ClaseDiariaResolution resolucion) {
        return switch (resolucion) {
            case Disponible d -> new ClaseDeHoy(Estado.DISPONIBLE, d.programDay(), d.cursoId().value(),
                    d.cursoTitulo(), d.leccionId().value(), d.leccionTitulo(), d.leccionCompletada());
            case NoIniciado ignored -> new ClaseDeHoy(Estado.NO_INICIADO, 0, null, null, null, null, false);
            case Proximamente p -> new ClaseDeHoy(Estado.PROXIMAMENTE, p.programDay(), null, null, null, null, false);
        };
    }
}
