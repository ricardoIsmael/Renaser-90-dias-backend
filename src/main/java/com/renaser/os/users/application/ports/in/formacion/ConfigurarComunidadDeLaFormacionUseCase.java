package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Administración y Alquimista prenden o apagan un tipo de comunidad de una formación (spec 024, FR-034). Apagarlo lo esconde de toda la formación sin
 * borrar sus mensajes; prenderlo los devuelve. No pide una app nueva (FR-036).
 */
public interface ConfigurarComunidadDeLaFormacionUseCase {

    /**
     * @return los tipos prendidos después del cambio
     * @throws com.renaser.os.shared.domain.NotAuthorizedException (403) si no es ADMIN/ALCHEMIST activo
     */
    Set<TipoDeComunidad> fijar(FijarTipoCommand command);

    record FijarTipoCommand(UserId actor, Formacion formacion, TipoDeComunidad tipo, boolean prendido) {
    }
}
