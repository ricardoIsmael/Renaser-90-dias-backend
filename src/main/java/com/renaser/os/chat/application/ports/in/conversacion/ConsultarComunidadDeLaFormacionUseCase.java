package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Los tipos de comunidad de una formación para quien los administra (spec 024, FR-033, FR-035): cuáles están prendidos y cuántas conversaciones
 * tiene cada uno, para que la confirmación de apagar uno diga cuántas esconde. Solo Administración y Alquimista activos.
 */
public interface ConsultarComunidadDeLaFormacionUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException (403) si no es ADMIN/ALCHEMIST con la cuenta activa */
    List<TipoDeLaComunidad> consultar(UserId actorId, Formacion formacion);

    /** @param conversaciones cuántas conversaciones de ese tipo tiene la formación (las que se esconden al apagarlo) */
    record TipoDeLaComunidad(TipoDeComunidad tipo, boolean prendido, long conversaciones) {
    }
}
