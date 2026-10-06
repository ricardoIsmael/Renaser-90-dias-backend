package com.renaser.os.calendar.application.ports.out.persona;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;

/**
 * Nombre y foto de varias personas en una sola consulta, para las listas de asistencia (D-256). Lo resuelve
 * {@code users.api.UserSummaryFinder}: {@code calendar} no lee {@code usuarios} por su cuenta. Los ids que no
 * existen no aparecen en el mapa.
 */
public interface ConsultarPersonasPort {

    Map<UserId, Persona> porIds(Collection<UserId> ids);

    record Persona(UserId id, String nombre, String avatarUrl) {
    }
}
