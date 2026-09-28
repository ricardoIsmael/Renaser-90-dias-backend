package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.caja.AvisarCajaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.onboarding.api.AvisoDeCaja;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Los avisos de la Caja Renaser en el chat de soporte (D-219): un mensaje del programa, corto (pedido del
 * dueño: poco texto). Cuando sale, va primero la foto de la caja armada.
 *
 * <p><b>La foto se copia al depósito del chat</b> ({@code chat/<soporte>/fotos/…}): la media de un mensaje
 * vive bajo el prefijo de su conversación, y la de la caja está bajo {@code onboarding/}. Si el almacenamiento
 * es de marcador o la foto ya no está, sale solo el texto.
 */
@Service
public class CajaEnSoporteService implements AvisarCajaEnSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(CajaEnSoporteService.class);
    private static final long PESO_MAXIMO_FOTO = 10L * 1024 * 1024;
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final LoadConversacionPort conversaciones;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final AlmacenamientoPort almacenamiento;
    private final IdGenerator idGenerator;

    CajaEnSoporteService(LoadConversacionPort conversaciones, EnviarMensajeDelProgramaUseCase delPrograma,
                         AlmacenamientoPort almacenamiento, IdGenerator idGenerator) {
        this.conversaciones = conversaciones;
        this.delPrograma = delPrograma;
        this.almacenamiento = almacenamiento;
        this.idGenerator = idGenerator;
    }

    @Override
    public void avisar(AvisoDeCajaEvent aviso) {
        if (!aviso.aviso().alAprendiz()) {
            return;
        }
        Optional<Conversacion> soporte = conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(aviso.aprendizId()));
        if (soporte.isEmpty()) {
            log.info("[chat.caja] {} no tiene chat de soporte: el aviso {} queda solo en la bandeja",
                    aviso.aprendizId(), aviso.aviso());
            return;
        }
        ConversacionId id = soporte.get().id();
        if (aviso.aviso() == AvisoDeCaja.EN_CAMINO) {
            fotoDeLaCaja(id, aviso.fotoRuta()).ifPresent(foto -> delPrograma.enviarDelPrograma(id, aviso.aprendizId(), foto));
        }
        delPrograma.enviarDelPrograma(id, aviso.aprendizId(), ContenidoDelPrograma.texto(texto(aviso)));
    }

    private Optional<ContenidoDelPrograma> fotoDeLaCaja(ConversacionId soporteId, String rutaDeLaCaja) {
        if (rutaDeLaCaja == null || !almacenamiento.guardaObjetos()) {
            return Optional.empty();
        }
        try {
            return almacenamiento.leer(rutaDeLaCaja, PESO_MAXIMO_FOTO).map(bytes -> {
                String mime = esPng(bytes) ? "image/png" : "image/jpeg";
                String ruta = "chat/" + soporteId.value() + "/fotos/" + idGenerator.newId();
                almacenamiento.subir(ruta, bytes, mime);
                return ContenidoDelPrograma.imagen(ruta, mime, bytes.length);
            });
        } catch (RuntimeException sinFoto) {
            log.warn("[chat.caja] la foto de la caja {} no se pudo copiar al soporte ({}): sale solo el texto",
                    rutaDeLaCaja, sinFoto.getMessage());
            return Optional.empty();
        }
    }

    static String texto(AvisoDeCajaEvent aviso) {
        return switch (aviso.aviso()) {
            case EN_REVISION, APROBADA -> "Tu Caja Renaser está en revisión.";
            case ARMANDO -> "Estamos armando tu Caja Renaser.";
            case EN_CAMINO -> "Tu Caja Renaser va en camino. " + envio(aviso);
            case RECORDATORIO -> "¿Ya te llegó tu Caja Renaser? Confírmalo en Yo → Tu Caja Renaser.";
            case ENTREGADA -> "¡Tu Caja Renaser llegó! Si quieres, comparte una foto en el Muro.";
            case SIN_CONFIRMAR -> throw new IllegalArgumentException("Ese aviso es para el Admin");
        };
    }

    private static String envio(AvisoDeCajaEvent aviso) {
        String por = aviso.courier() == null ? aviso.medio() : aviso.courier();
        return "Por " + por + ", código " + aviso.codigo() + ".";
    }

    private static boolean esPng(byte[] bytes) {
        if (bytes.length < FIRMA_PNG.length) {
            return false;
        }
        for (int i = 0; i < FIRMA_PNG.length; i++) {
            if (bytes[i] != FIRMA_PNG[i]) {
                return false;
            }
        }
        return true;
    }
}
