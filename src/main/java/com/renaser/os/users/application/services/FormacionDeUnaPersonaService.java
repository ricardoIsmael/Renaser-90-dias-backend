package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeUnaPersonaUseCase;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.ParticipacionesAnterioresPort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/** La ficha de formaciones de una persona para Administración (spec 022). El recurso se carga primero y el guard después (E-42). */
@Service
class FormacionDeUnaPersonaService implements ConsultarFormacionDeUnaPersonaUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final CargarPertenenciaPort pertenencias;
    private final ParticipacionesAnterioresPort historial;

    FormacionDeUnaPersonaService(RequireAdminGuard requireAdminGuard, CargarPertenenciaPort pertenencias,
                                 ParticipacionesAnterioresPort historial) {
        this.requireAdminGuard = requireAdminGuard;
        this.pertenencias = pertenencias;
        this.historial = historial;
    }

    @Override
    public FormacionDeUnaPersona consultar(UserId actor, UserId persona) {
        var pertenencia = pertenencias.de(persona).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + persona));
        requireAdminGuard.requireAdminActivo(actor);
        int duracion = pertenencia.deLaParticipacion() == null ? DuracionDelPrograma.POR_DEFECTO : pertenencia.duracionDias();
        return new FormacionDeUnaPersona(pertenencia.deLaParticipacion(), duracion, pertenencia.programaActivado(),
                pertenencia.asignadas(), historial.de(persona));
    }
}
