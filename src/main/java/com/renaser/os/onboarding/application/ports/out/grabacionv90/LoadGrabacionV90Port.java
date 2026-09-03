package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadGrabacionV90Port {

    Optional<GrabacionV90> porId(long id);

    /**
     * Version con bloqueo pesimista para el camino de ESCRITURA de
     * {@code GrabacionV90Service.solicitarValidacion} (C-3,
     * docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): sin el, dos
     * solicitudes concurrentes leen ambas {@code PENDIENTE} antes de que cualquiera escriba,
     * y las dos pasan el guard en memoria de {@code GrabacionV90.procesarIntentoDeValidacion()},
     * disparando dos llamadas a la IA para la misma grabacion. Mismo patron que
     * {@code LoadRegistroHabitoPort.byIdParaEscritura} en {@code habits}.
     */
    Optional<GrabacionV90> porIdParaEscritura(long id);

    Optional<GrabacionV90> porSlot(UserId usuarioId, String fase, String eje, short indice);

    List<GrabacionV90> todasDeUsuario(UserId usuarioId);

    /** Dashboard admin de onboarding (gap #8): cuantas grabaciones hay en cada estado de validacion IA. */
    long contarPorEstado(EstadoIAv90 estado);
}
