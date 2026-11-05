package com.renaser.os.habits.application.ports.in.carrera;

import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Spec 042 (FR-041): la ruta de una carrera propia. Vacío si no se guardó, si ya se borró por antigüedad (FR-042) o si la carrera
 * no existe o no es de quien la pide (el 404 no cuenta si existe para otro). Es de la propia persona: no deja constancia.
 */
public interface ConsultarRutaPropiaUseCase {

    Optional<RutaDeCarrera> rutaPropia(UserId actorId, UUID carreraId);
}
