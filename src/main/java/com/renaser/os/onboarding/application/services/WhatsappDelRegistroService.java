package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.respuesta.SembrarRespuestaPorClavePort;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.WhatsappDelRegistro;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * El WhatsApp del alta pasa a la Ficha Inicial al aceptar la solicitud (spec 043, FR-006): la persona lo encuentra ya escrito en su ficha (la app la
 * rellena con lo guardado) y en Yo → Editar perfil. Se guarda tal como lo arma la app ({@code "+51 987654321"}), en la misma respuesta de siempre
 * ({@link FichaDeEnvio#CLAVE_CELULAR}); nunca se pisa uno que ya exista.
 */
@Service
class WhatsappDelRegistroService implements WhatsappDelRegistro {

    /** La misma forma que exigen los recordatorios por WhatsApp (spec 041): {@code +}, codigo de pais y de 8 a 15 digitos en total. */
    private static final Pattern INTERNACIONAL = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final SembrarRespuestaPorClavePort respuestas;
    private final Clock clock;

    WhatsappDelRegistroService(SembrarRespuestaPorClavePort respuestas, Clock clock) {
        this.respuestas = respuestas;
        this.clock = clock;
    }

    @Override
    public void llevarALaFicha(UserId persona, String whatsapp) {
        String limpio = whatsapp == null ? "" : whatsapp.strip();
        if (!INTERNACIONAL.matcher(limpio.replaceAll("[\\s().-]", "")).matches()) {
            return;
        }
        respuestas.siNoHay(persona, FichaDeEnvio.CLAVE_CELULAR, limpio, clock.now());
    }
}
