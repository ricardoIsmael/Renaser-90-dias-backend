package com.renaser.os.calendar.application.ports.out.persona;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;

/**
 * El correo de inicio de sesión de quien crea o edita un evento con Meet automático, para invitarlo al evento de Calendar (spec 040, decisión 6 del
 * dueño, FR-035). Lo resuelve {@code users.api.CorreosDeUsuariosFinder}: {@code calendar} no lee {@code usuarios} por su cuenta. Solo se pide para
 * ADMIN y ALCHEMIST (los únicos que crean eventos con Meet); el correo de un aprendiz nunca viaja a Google.
 */
public interface CorreosDePersonasPort {

    /** Los ids que no existen no aparecen en el mapa. */
    Map<UserId, String> correosDe(Collection<UserId> ids);
}
