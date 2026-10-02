package com.renaser.os.users.application.ports.out.emergencia;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;

import java.util.Optional;
import java.util.UUID;

/** Los pedidos de emergencia (V90). Solo lo que hoy lee alguna pantalla: la abierta de una persona y una por id. */
public interface LoadSolicitudDeEmergenciaPort {

    /** La abierta de esa persona; hay a lo sumo una (índice único parcial de V90). */
    Optional<SolicitudDeEmergencia> abiertaDe(UserId aprendizId);

    Optional<SolicitudDeEmergencia> porId(UUID id);
}
