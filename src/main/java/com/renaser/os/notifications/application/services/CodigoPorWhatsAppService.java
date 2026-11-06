package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.whatsapp.EnvioDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.NumeroDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.shared.infrastructure.async.EjecucionAsincronaConfig;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * El codigo de verificacion por WhatsApp (spec 043, D-325): lo que {@code users} le pide a este modulo, dueño del canal (YCloud, spec 041). Normaliza el
 * numero con la misma regla que los recordatorios ({@link NumeroDeWhatsApp}) y manda la plantilla de AUTENTICACION. Solo escribe el numero enmascarado en
 * el registro y nunca el codigo (FR-011).
 */
@Service
class CodigoPorWhatsAppService implements CodigoPorWhatsApp {

    private static final Logger log = LoggerFactory.getLogger(CodigoPorWhatsAppService.class);

    private final EnvioDeWhatsAppPort envio;

    CodigoPorWhatsAppService(EnvioDeWhatsAppPort envio) {
        this.envio = envio;
    }

    @Override
    public boolean configurado() {
        return envio.configuradoParaCodigos();
    }

    @Override
    public Optional<NumeroParaCodigo> numero(String texto) {
        return NumeroDeWhatsApp.desdeLaFicha(texto).map(numero -> new NumeroParaCodigo(numero.valor(), numero.enmascarado()));
    }

    /** {@code NO_SE_SUPO} (un corte o un 5xx) cuenta como enviado: pudo haber salido y no se reintenta. */
    @Override
    public boolean enviar(NumeroParaCodigo numero, String codigo) {
        ResultadoDelEnvio resultado = envio.enviarCodigo(numero.valor(), codigo);
        boolean salio = resultado.estado() != EstadoDeEntrega.FALLIDO;
        if (salio) {
            log.info("[notifications.codigo-whatsapp] codigo enviado a {} ({})", numero.enmascarado(), resultado.estado());
        } else {
            log.warn("[notifications.codigo-whatsapp] el codigo no salio hacia {} ({}: {})", numero.enmascarado(), resultado.causa(), resultado.motivo());
        }
        return salio;
    }

    @Async(EjecucionAsincronaConfig.EJECUTOR_DE_EVENTOS)
    @Override
    public void enviarSinEsperar(NumeroParaCodigo numero, String codigo) {
        enviar(numero, codigo);
    }
}
