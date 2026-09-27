package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * La bienvenida automática del procedimiento OPE-01-01 (D-174): la tarjeta de Canva con el primer
 * nombre, el mensaje que la acompaña y el mensaje formal de bienvenida, como se manda por WhatsApp.
 *
 * <p><b>La firma el programa, no una persona</b> (D-199, 2026-09-27): los tres salen como mensajes de
 * SISTEMA ({@link EnviarMensajeDelProgramaUseCase}), que las apps muestran como «Formación Renaser».
 * <blockquote><b>Corregido 2026-09-27 (D-199).</b> Los firmaba la cuenta de staff de
 * {@code BIENVENIDA_REMITENTE_EMAIL} (la de Kelin), que tenía que ser ADMIN/ALCHEMIST y participar del
 * soporte (E-330). El dueño decidió que salgan del programa: ya no hay remitente que configurar ni
 * validar.</blockquote>
 *
 * <p><b>Los textos salen de un recurso versionado</b> ({@link TextosDeBienvenidaPort}, D-190), no de
 * una variable de entorno: se cambian editando {@code bienvenida/mensajes.yaml} y redesplegando.
 *
 * <p><b>Mensajes separados y no una foto con texto:</b> el APK publicado muestra el texto de una foto
 * solo cuando la foto no carga, así que en un solo mensaje el texto no se vería.
 *
 * <p><b>Idempotente</b> (G-2, 2026-09-26). El evento que la dispara queda en el outbox de Modulith y
 * se reentrega si el proceso muere a mitad de camino o si falla. La marca es
 * {@code mensajes_bienvenida} ({@link MarcaDeBienvenidaPort}): se mira antes de dibujar, y se deja
 * en la MISMA transacción que guarda los mensajes. O quedan los mensajes y la marca, o nada; si
 * dos entregas se cruzan, la PK de la marca deshace la que perdió. Un fallo se lanza para que el
 * outbox reintente.
 *
 * <p><b>Sin almacenamiento de verdad no hay tarjeta</b> (G-5): con el adaptador de marcador (local,
 * pruebas) subir no guarda nada, y el mensaje apuntaría a una foto inexistente. Se manda solo el
 * mensaje formal (el que acompaña la tarjeta no tiene sentido sin ella) y queda en el log.
 *
 * <p>Dibujar y subir a S3 van FUERA de la transacción: no deben retener una conexión de la base.
 *
 * <p><b>Apagada salvo {@code BIENVENIDA_ACTIVA=true}</b> (D-199, 2026-09-27;
 * {@code renaser.chat.bienvenida.activa}). Los textos son borradores y no pueden llegar a aprendices
 * reales hasta que el dueño los apruebe. Apagada no mira nada: no dibuja, no sube, no manda y no deja
 * marca, así que prenderla no reenvía bienvenidas atrasadas (el evento de un aprendiz que entró con
 * ella apagada ya se dio por completado).
 */
@Service
public class BienvenidaEnSoporteService implements DarBienvenidaEnSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaEnSoporteService.class);
    private static final String MARCA_NOMBRE = "{nombre}";

    private final DibujarBienvenidaPort dibujarPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final UserSummaryFinder userSummaryFinder;
    private final MarcaDeBienvenidaPort marcaPort;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transaccion;
    private final TextosDeBienvenidaPort textos;
    private final boolean activa;

    public BienvenidaEnSoporteService(DibujarBienvenidaPort dibujarPort, AlmacenamientoPort almacenamientoPort,
                                       EnviarMensajeDelProgramaUseCase delPrograma, UserSummaryFinder userSummaryFinder,
                                       MarcaDeBienvenidaPort marcaPort, TextosDeBienvenidaPort textos,
                                       IdGenerator idGenerator, PlatformTransactionManager transactionManager,
                                       @Value("${renaser.chat.bienvenida.activa:false}") boolean activa) {
        this.dibujarPort = dibujarPort;
        this.almacenamientoPort = almacenamientoPort;
        this.delPrograma = delPrograma;
        this.userSummaryFinder = userSummaryFinder;
        this.marcaPort = marcaPort;
        this.idGenerator = idGenerator;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.textos = textos;
        this.activa = activa;
        if (!activa) {
            log.info("[chat.bienvenida] bienvenidas automáticas apagadas (BIENVENIDA_ACTIVA=false): "
                    + "ni la del soporte ni la del grupo salen");
        }
    }

    @Override
    public void darBienvenida(ConversacionId soporteId, UserId aprendizId) {
        if (!activa) {
            return;
        }
        if (marcaPort.yaSeDio(aprendizId)) {
            log.debug("[chat.bienvenida] {} ya tiene su bienvenida: reentrega sin efecto", aprendizId);
            return;
        }
        String nombre = userSummaryFinder.findById(aprendizId).map(u -> PrimerNombre.de(u.fullName())).orElse("");
        Optional<TarjetaSubida> tarjeta = subirTarjeta(soporteId, nombre);
        try {
            transaccion.executeWithoutResult(status -> enviarYMarcar(soporteId, aprendizId, tarjeta, nombre));
        } catch (DataIntegrityViolationException e) {
            if (!marcaPort.yaSeDio(aprendizId)) {
                throw e; // no era la carrera con otra entrega: que el outbox reintente
            }
            log.debug("[chat.bienvenida] otra entrega ya dio la bienvenida a {}; esta se deshizo", aprendizId);
        }
    }

    /**
     * Dibuja y sube la tarjeta. Vacío si el almacenamiento es de marcador (G-5): el objeto no
     * existiría. La ruta va bajo el prefijo del propio chat, como toda media de una conversación.
     */
    private Optional<TarjetaSubida> subirTarjeta(ConversacionId soporteId, String nombre) {
        if (!almacenamientoPort.guardaObjetos()) {
            log.warn("[chat.bienvenida] el almacenamiento no guarda objetos (noop): se manda solo el texto al soporte {}",
                    soporteId);
            return Optional.empty();
        }
        byte[] contenido = dibujarPort.dibujar(nombre);
        String ruta = "chat/" + soporteId.value() + "/fotos/" + idGenerator.newId();
        almacenamientoPort.subir(ruta, contenido, DibujarBienvenidaPort.TIPO_CONTENIDO);
        return Optional.of(new TarjetaSubida(ruta, contenido.length));
    }

    /**
     * Los mensajes y la marca, juntos, en el orden del procedimiento: tarjeta, el mensaje que la
     * acompaña (solo si hubo tarjeta) y el formal. Sin ningún mensaje no hay marca que dejar. Cada
     * mensaje del programa se guarda a nombre del aprendiz, que es de quien habla (ver {@code Mensaje}).
     */
    private void enviarYMarcar(ConversacionId soporteId, UserId aprendizId, Optional<TarjetaSubida> tarjeta,
                               String nombre) {
        List<Mensaje> enviados = new ArrayList<>();
        tarjeta.ifPresent(t -> {
            enviados.add(delPrograma.enviarDelPrograma(soporteId, aprendizId,
                    ContenidoDelPrograma.imagen(t.ruta(), DibujarBienvenidaPort.TIPO_CONTENIDO, t.bytes())));
            enviarTexto(soporteId, aprendizId, textos.soporteConLaTarjeta(), nombre).ifPresent(enviados::add);
        });
        enviarTexto(soporteId, aprendizId, textos.soporteFormal(), nombre).ifPresent(enviados::add);
        if (!enviados.isEmpty()) {
            marcaPort.marcar(aprendizId, enviados.get(0).id());
        }
    }

    /** @return el mensaje, o vacío si ese texto está vacío en el recurso */
    private Optional<Mensaje> enviarTexto(ConversacionId soporteId, UserId aprendizId, String plantilla,
                                          String nombre) {
        if (plantilla.isEmpty()) {
            return Optional.empty();
        }
        String texto = plantilla.replace(MARCA_NOMBRE, nombre);
        return Optional.of(delPrograma.enviarDelPrograma(soporteId, aprendizId, ContenidoDelPrograma.texto(texto)));
    }

    private record TarjetaSubida(String ruta, int bytes) {
    }
}
