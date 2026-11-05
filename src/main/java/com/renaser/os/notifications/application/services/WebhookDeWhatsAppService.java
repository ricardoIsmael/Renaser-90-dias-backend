package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase;
import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.PalabrasDeBaja;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Atiende lo que YCloud avisa al webhook (spec 041, FR-026 y FR-027): el estado de un mensaje nuestro (entregado, leido, fallo) y las respuestas de las
 * personas, de las que SOLO se atiende la baja. Lo que no es un recordatorio nuestro no toca nada, no se contesta y no se guarda.
 *
 * <p>Una baja vale cuando (1) la persona toco el boton «No recibir más» de un mensaje nuestro (la carga lleva el id de la entrega) o (2) escribio ENTERO «baja»
 * (o una de sus primas) <b>respondiendo a un mensaje nuestro</b> (el {@code context.id} es el wamid que guardamos). Un «BAJA» suelto no pasa nada: puede ser de
 * cualquier otra conversacion de ese numero. La persona dada de baja es la dueña de la entrega, no la que "dice" el numero: el id del boton y el wamid de un
 * mensaje solo los tiene quien lo recibio.
 */
@Service
public class WebhookDeWhatsAppService implements AtenderWebhookDeWhatsAppUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeWhatsAppService.class);

    private final EntregasDeWhatsAppPort entregas;
    private final BajaDeWhatsAppService baja;
    private final AvisoDeFallaDeWhatsApp avisoDeFalla;
    private final Clock clock;

    WebhookDeWhatsAppService(EntregasDeWhatsAppPort entregas, BajaDeWhatsAppService baja, AvisoDeFallaDeWhatsApp avisoDeFalla, Clock clock) {
        this.entregas = entregas;
        this.baja = baja;
        this.avisoDeFalla = avisoDeFalla;
        this.clock = clock;
    }

    @Override
    public void estadoDeUnMensaje(EstadoDeUnMensaje estado) {
        Optional<EstadoDeEntrega> nuevo = EstadoDeEntrega.delProveedor(estado.estado());
        if (nuevo.isEmpty()) {
            return;
        }
        Optional<EntregaDeWhatsApp> entrega = laEntregaDe(estado);
        if (entrega.isEmpty()) {
            return;
        }
        boolean fallo = nuevo.get() == EstadoDeEntrega.FALLIDO;
        boolean seAplico = entregas.avanzarEstado(entrega.get().id(), nuevo.get(), fallo ? estado.error() : null, estado.wamid(),
                estado.proveedorId(), clock.now());
        if (seAplico && fallo) {
            avisoDeFalla.avisar(CausaDeFalla.segun(null, estado.error()));
        }
    }

    @Override
    public void respuestaRecibida(RespuestaRecibida respuesta) {
        Optional<EntregaDeWhatsApp> deQuien = respuesta.esBoton() ? deUnBoton(respuesta) : deUnTexto(respuesta);
        if (deQuien.isEmpty()) {
            log.debug("[notifications.evento-whatsapp] respuesta ignorada: no es una baja de un mensaje nuestro");
            return;
        }
        baja.darDeBaja(deQuien.get().usuarioId(), respuesta.de());
    }

    /** El toque de un boton nuestro; una carga ajena (otro boton de la cuenta) no lo es, y su texto no cuenta como baja. */
    private Optional<EntregaDeWhatsApp> deUnBoton(RespuestaRecibida respuesta) {
        return PalabrasDeBaja.entregaDeLaCarga(respuesta.carga()).flatMap(entregas::porId);
    }

    private Optional<EntregaDeWhatsApp> deUnTexto(RespuestaRecibida respuesta) {
        if (!PalabrasDeBaja.esBaja(respuesta.texto()) || respuesta.respondeAWamid() == null || respuesta.respondeAWamid().isBlank()) {
            return Optional.empty();
        }
        return entregas.porWamid(respuesta.respondeAWamid().strip());
    }

    private Optional<EntregaDeWhatsApp> laEntregaDe(EstadoDeUnMensaje estado) {
        Optional<EntregaDeWhatsApp> porNuestroId = idDe(estado.externalId()).flatMap(entregas::porId);
        if (porNuestroId.isPresent() || estado.proveedorId() == null || estado.proveedorId().isBlank()) {
            return porNuestroId;
        }
        return entregas.porProveedorId(estado.proveedorId().strip());
    }

    private static Optional<UUID> idDe(String texto) {
        try {
            return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(texto.strip()));
        } catch (IllegalArgumentException noEsNuestro) {
            return Optional.empty();
        }
    }
}
