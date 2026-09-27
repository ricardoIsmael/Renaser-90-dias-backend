package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * La bienvenida automática del procedimiento OPE-01-01 (D-174): la tarjeta de Canva con el primer
 * nombre, el mensaje que la acompaña y el mensaje formal de bienvenida, como se manda por WhatsApp,
 * desde la cuenta de staff configurada (hoy la de Kelin).
 *
 * <p><b>Los textos salen de un recurso versionado</b> ({@link TextosDeBienvenidaPort}, D-190), no de
 * una variable de entorno: se cambian editando {@code bienvenida/mensajes.yaml} y redesplegando.
 * <blockquote><b>Corregido 2026-09-26 (D-190).</b> Antes había un solo texto, en
 * {@code renaser.bienvenida.texto} ({@code BIENVENIDA_TEXTO}). El dueño pidió que el texto no vaya
 * en el entorno y que cada parte del procedimiento tenga su mensaje.</blockquote>
 *
 * <p><b>Mensajes separados y no una foto con texto:</b> la app instalada muestra el texto de una foto
 * solo cuando la foto no carga, así que en un solo mensaje el texto no se vería.
 *
 * <p><b>Idempotente</b> (G-2, 2026-09-26). El evento que la dispara queda en el outbox de Modulith y
 * se reentrega si el proceso muere a mitad de camino o si falla. La marca es
 * {@code mensajes_bienvenida} ({@link MarcaDeBienvenidaPort}): se mira antes de dibujar, y se deja
 * en la MISMA transacción que guarda los dos mensajes. O quedan los mensajes y la marca, o nada; si
 * dos entregas se cruzan, la PK de la marca deshace la que perdió.
 *
 * <p><b>Un fallo se lanza para que el outbox reintente</b> (G-2). Antes se tragaba para no duplicar;
 * con la marca, reintentar ya no duplica. Lo que es configuración (sin remitente, remitente
 * suspendido, remitente que no es staff del soporte) no es un fallo: no se manda, no se marca y no
 * se reintenta.
 *
 * <p><b>El remitente tiene que poder escribir en ESE soporte</b> (E-330): rol ADMIN/ALCHEMIST (el
 * staff que {@link ConversacionSoporteService} mete en todo soporte) y participante de la
 * conversación. Si no, {@code MensajeService} rechaza el envío con {@code NotAuthorizedException}, y
 * como eso se lanzaba, el outbox lo reintentaba cada 5 minutos sin fin. Se mira antes de dibujar.
 *
 * <p><b>Sin almacenamiento de verdad no hay tarjeta</b> (G-5): con el adaptador de marcador (local,
 * pruebas) subir no guarda nada, y el mensaje apuntaría a una foto inexistente. Se manda solo el
 * mensaje formal (el que acompaña la tarjeta no tiene sentido sin ella) y queda en el log.
 *
 * <p>Dibujar y subir a S3 van FUERA de la transacción: no deben retener una conexión de la base.
 */
@Service
public class BienvenidaEnSoporteService implements DarBienvenidaEnSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaEnSoporteService.class);
    private static final String MARCA_NOMBRE = "{nombre}";

    private final DibujarBienvenidaPort dibujarPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final EnviarMensajeUseCase enviarMensaje;
    private final UserSummaryFinder userSummaryFinder;
    private final MarcaDeBienvenidaPort marcaPort;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transaccion;
    private final String remitenteEmail;
    private final TextosDeBienvenidaPort textos;
    private final EsParticipantePort esParticipantePort;

    public BienvenidaEnSoporteService(DibujarBienvenidaPort dibujarPort, AlmacenamientoPort almacenamientoPort,
                                       EnviarMensajeUseCase enviarMensaje, UserSummaryFinder userSummaryFinder,
                                       MarcaDeBienvenidaPort marcaPort, TextosDeBienvenidaPort textos,
                                       EsParticipantePort esParticipantePort, IdGenerator idGenerator,
                                       PlatformTransactionManager transactionManager,
                                       @Value("${renaser.bienvenida.remitente-email:}") String remitenteEmail) {
        this.dibujarPort = dibujarPort;
        this.almacenamientoPort = almacenamientoPort;
        this.enviarMensaje = enviarMensaje;
        this.userSummaryFinder = userSummaryFinder;
        this.marcaPort = marcaPort;
        this.idGenerator = idGenerator;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.remitenteEmail = remitenteEmail == null ? "" : remitenteEmail.strip();
        this.textos = textos;
        this.esParticipantePort = esParticipantePort;
    }

    @Override
    public void revisarRemitenteConfigurado() {
        if (remitenteEmail.isEmpty()) {
            return;
        }
        Optional<UserSummary> remitente = remitenteActivo();
        if (remitente.isEmpty()) {
            log.warn("[chat.bienvenida] BIENVENIDA_REMITENTE_EMAIL={} no existe o no está activo: bienvenida de soporte "
                    + "apagada. Configura la cuenta de staff que firma (Kelin)", remitenteEmail);
        } else if (!esStaffDelSoporte(remitente.get())) {
            avisarRolFueraDelSoporte(remitente.get(), "todos los aprendices nuevos");
        }
    }

    @Override
    public void darBienvenida(ConversacionId soporteId, UserId aprendizId) {
        if (remitenteEmail.isEmpty()) {
            return;
        }
        if (marcaPort.yaSeDio(aprendizId)) {
            log.debug("[chat.bienvenida] {} ya tiene su bienvenida: reentrega sin efecto", aprendizId);
            return;
        }
        Optional<UserSummary> remitente = remitenteActivo();
        if (remitente.isEmpty()) {
            log.warn("[chat.bienvenida] el remitente {} no existe o no está activo: no se manda la bienvenida", remitenteEmail);
            return;
        }
        if (!puedeEscribirEnElSoporte(remitente.get(), soporteId, aprendizId)) {
            return;
        }
        String nombre = userSummaryFinder.findById(aprendizId).map(u -> PrimerNombre.de(u.fullName())).orElse("");
        Optional<TarjetaSubida> tarjeta = subirTarjeta(soporteId, nombre);
        try {
            transaccion.executeWithoutResult(status ->
                    enviarYMarcar(remitente.get().id(), soporteId, aprendizId, tarjeta, nombre));
        } catch (DataIntegrityViolationException e) {
            if (!marcaPort.yaSeDio(aprendizId)) {
                throw e; // no era la carrera con otra entrega: que el outbox reintente
            }
            log.debug("[chat.bienvenida] otra entrega ya dio la bienvenida a {}; esta se deshizo", aprendizId);
        }
    }

    private Optional<UserSummary> remitenteActivo() {
        return userSummaryFinder.findByEmail(remitenteEmail).filter(u -> u.status() == UserStatus.ACTIVE);
    }

    /**
     * Lo mismo que {@code MensajeService} va a exigir al enviar, mirado ANTES de dibujar y sin lanzar
     * (E-330): un remitente que no puede escribir es configuración inválida, y lanzar solo hacía que
     * el outbox reintentara cada 5 minutos para siempre. Sin marca: la bienvenida se puede dar después.
     */
    private boolean puedeEscribirEnElSoporte(UserSummary remitente, ConversacionId soporteId, UserId aprendizId) {
        if (!esStaffDelSoporte(remitente)) {
            avisarRolFueraDelSoporte(remitente, "el aprendiz " + aprendizId + " (sin marca, se puede mandar a mano)");
            return false;
        }
        if (!esParticipantePort.esParticipante(soporteId, remitente.id())) {
            log.warn("[chat.bienvenida] BIENVENIDA_REMITENTE_EMAIL={} no participa del soporte {}: bienvenida de {} "
                    + "apagada (sin marca, se puede mandar a mano)", remitenteEmail, soporteId, aprendizId);
            return false;
        }
        return true;
    }

    private static boolean esStaffDelSoporte(UserSummary remitente) {
        return ConversacionSoporteService.STAFF_ADMINISTRATIVO.contains(remitente.role());
    }

    private void avisarRolFueraDelSoporte(UserSummary remitente, String afectados) {
        log.warn("[chat.bienvenida] BIENVENIDA_REMITENTE_EMAIL={} tiene rol {}, no es ADMIN/ALCHEMIST del soporte: "
                + "bienvenida apagada para {}. Configura la cuenta de staff que firma (Kelin)",
                remitenteEmail, remitente.role(), afectados);
    }

    /**
     * Dibuja y sube la tarjeta. Vacío si el almacenamiento es de marcador (G-5): el objeto no
     * existiría. La ruta va bajo el prefijo del propio chat: es lo único que {@code MensajeService} acepta.
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
     * acompaña (solo si hubo tarjeta) y el formal. Sin ningún mensaje no hay marca que dejar.
     */
    private void enviarYMarcar(UserId remitenteId, ConversacionId soporteId, UserId aprendizId,
                               Optional<TarjetaSubida> tarjeta, String nombre) {
        List<Mensaje> enviados = new ArrayList<>();
        tarjeta.ifPresent(t -> {
            enviados.add(enviarTarjeta(remitenteId, soporteId, t));
            enviarTexto(remitenteId, soporteId, textos.soporteConLaTarjeta(), nombre).ifPresent(enviados::add);
        });
        enviarTexto(remitenteId, soporteId, textos.soporteFormal(), nombre).ifPresent(enviados::add);
        if (!enviados.isEmpty()) {
            marcaPort.marcar(aprendizId, enviados.get(0).id());
        }
    }

    private Mensaje enviarTarjeta(UserId remitenteId, ConversacionId soporteId, TarjetaSubida tarjeta) {
        return enviarMensaje.enviar(new EnviarMensajeCommand(remitenteId, soporteId, TipoMensaje.IMAGEN, null,
                Mensaje.BUCKET_DEFAULT, tarjeta.ruta(), DibujarBienvenidaPort.TIPO_CONTENIDO, tarjeta.bytes(), null,
                null, OrigenMedia.CLIENTE));
    }

    /** @return el mensaje, o vacío si ese texto está vacío en el recurso */
    private Optional<Mensaje> enviarTexto(UserId remitenteId, ConversacionId soporteId, String plantilla,
                                          String nombre) {
        if (plantilla.isEmpty()) {
            return Optional.empty();
        }
        String mensaje = plantilla.replace(MARCA_NOMBRE, nombre);
        return Optional.of(enviarMensaje.enviar(new EnviarMensajeCommand(remitenteId, soporteId, TipoMensaje.TEXTO,
                mensaje, null, null, null, null, null, null, OrigenMedia.CLIENTE)));
    }

    private record TarjetaSubida(String ruta, int bytes) {
    }
}
