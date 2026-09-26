package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * La bienvenida automática del procedimiento OPE-01-01 (D-174): la tarjeta de Canva con el primer
 * nombre y después el texto, como se manda por WhatsApp, desde la cuenta de staff configurada
 * (hoy la de Kelin).
 *
 * <p><b>Dos mensajes y no una foto con texto:</b> la app instalada muestra el texto de una foto
 * solo cuando la foto no carga, así que en un solo mensaje el texto no se vería.
 *
 * <p><b>Sin {@code @Transactional}:</b> dibujar y subir a S3 no deben retener una conexión de la
 * base; cada envío abre la suya dentro de {@link EnviarMensajeUseCase}.
 */
@Service
public class BienvenidaEnSoporteService implements DarBienvenidaEnSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaEnSoporteService.class);
    private static final String MARCA_NOMBRE = "{nombre}";

    private final DibujarBienvenidaPort dibujarPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final EnviarMensajeUseCase enviarMensaje;
    private final UserSummaryFinder userSummaryFinder;
    private final IdGenerator idGenerator;
    private final String remitenteEmail;
    private final String texto;

    public BienvenidaEnSoporteService(DibujarBienvenidaPort dibujarPort, AlmacenamientoPort almacenamientoPort,
                                       EnviarMensajeUseCase enviarMensaje, UserSummaryFinder userSummaryFinder,
                                       IdGenerator idGenerator,
                                       @Value("${renaser.bienvenida.remitente-email:}") String remitenteEmail,
                                       @Value("${renaser.bienvenida.texto:}") String texto) {
        this.dibujarPort = dibujarPort;
        this.almacenamientoPort = almacenamientoPort;
        this.enviarMensaje = enviarMensaje;
        this.userSummaryFinder = userSummaryFinder;
        this.idGenerator = idGenerator;
        this.remitenteEmail = remitenteEmail == null ? "" : remitenteEmail.strip();
        this.texto = texto == null ? "" : texto.strip();
    }

    @Override
    public void darBienvenida(ConversacionId soporteId, UserId aprendizId) {
        if (remitenteEmail.isEmpty()) {
            return;
        }
        Optional<UserSummary> remitente = remitenteActivo();
        if (remitente.isEmpty()) {
            log.warn("[chat.bienvenida] el remitente {} no existe o no está activo: no se manda la bienvenida", remitenteEmail);
            return;
        }
        try {
            String nombre = userSummaryFinder.findById(aprendizId).map(u -> PrimerNombre.de(u.fullName())).orElse("");
            enviarTarjeta(remitente.get().id(), soporteId, nombre);
            enviarTexto(remitente.get().id(), soporteId, nombre);
        } catch (RuntimeException e) {
            log.warn("[chat.bienvenida] no se pudo mandar la bienvenida al soporte {}", soporteId, e);
        }
    }

    private Optional<UserSummary> remitenteActivo() {
        return userSummaryFinder.findByEmail(remitenteEmail).filter(u -> u.status() == UserStatus.ACTIVE);
    }

    /** La ruta va bajo el prefijo del propio chat: es lo único que {@code MensajeService} acepta. */
    private void enviarTarjeta(UserId remitenteId, ConversacionId soporteId, String nombre) {
        byte[] tarjeta = dibujarPort.dibujar(nombre);
        String ruta = "chat/" + soporteId.value() + "/fotos/" + idGenerator.newId();
        almacenamientoPort.subir(ruta, tarjeta, DibujarBienvenidaPort.TIPO_CONTENIDO);
        enviarMensaje.enviar(new EnviarMensajeCommand(remitenteId, soporteId, TipoMensaje.IMAGEN, null,
                Mensaje.BUCKET_DEFAULT, ruta, DibujarBienvenidaPort.TIPO_CONTENIDO, tarjeta.length, null, null,
                OrigenMedia.CLIENTE));
    }

    private void enviarTexto(UserId remitenteId, ConversacionId soporteId, String nombre) {
        if (texto.isEmpty()) {
            return;
        }
        String mensaje = texto.replace(MARCA_NOMBRE, nombre.isEmpty() ? "" : nombre);
        enviarMensaje.enviar(new EnviarMensajeCommand(remitenteId, soporteId, TipoMensaje.TEXTO, mensaje,
                null, null, null, null, null, null, OrigenMedia.CLIENTE));
    }
}
