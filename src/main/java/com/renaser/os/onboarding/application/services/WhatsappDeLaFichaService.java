package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.WhatsappDeLaFichaFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * El WhatsApp de la Ficha Inicial de varias personas, en una sola consulta. Lo consume {@code users} por su {@code api} para
 * el panel de personas de Administración: la dependencia va de aquí hacia allá, como siempre.
 *
 * <p>Lee la misma respuesta que la Caja Renaser ({@link FichaDeEnvio#CLAVE_CELULAR}), por el mismo lector de respuestas por
 * clave: el dato vive en un solo lugar y no se copia. Un valor vacío cuenta como «no lo tiene».
 */
@Service
class WhatsappDeLaFichaService implements WhatsappDeLaFichaFinder {

    private final LeerRespuestasPorClavePort respuestas;

    WhatsappDeLaFichaService(LeerRespuestasPorClavePort respuestas) {
        this.respuestas = respuestas;
    }

    @Override
    public Map<UserId, String> de(Collection<UserId> personas) {
        Map<UserId, String> whatsapps = new HashMap<>();
        respuestas.textosDeVarios(personas, FichaDeEnvio.CLAVE_CELULAR).forEach((persona, texto) -> {
            String limpio = texto == null ? "" : texto.strip();
            if (!limpio.isEmpty()) {
                whatsapps.put(persona, limpio);
            }
        });
        return whatsapps;
    }
}
