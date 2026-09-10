package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido.RespuestaPreview;
import com.renaser.os.chat.application.ports.in.mensaje.SolicitarUrlSubidaMediaChatUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MensajeService implements EnviarMensajeUseCase, ListarMensajesUseCase,
        SolicitarUrlSubidaMediaChatUseCase {

    private static final int LIMITE_POR_DEFECTO = 30;
    private static final int LIMITE_MAXIMO = 100;
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final LoadConversacionPort loadConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final MarcarLeidoPort marcarLeidoPort;
    private final SaveMensajePort saveMensajePort;
    private final LoadMensajePort loadMensajePort;
    private final PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    private final UserSummaryFinder userSummaryFinder;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public MensajeService(LoadConversacionPort loadConversacionPort, EsParticipantePort esParticipantePort,
                           PertenenciaVigentePort pertenenciaVigentePort,
                           MarcarLeidoPort marcarLeidoPort, SaveMensajePort saveMensajePort,
                           LoadMensajePort loadMensajePort, PublicarMensajeFanoutPort publicarMensajeFanoutPort,
                           UserSummaryFinder userSummaryFinder, AlmacenamientoPort almacenamientoPort,
                           Clock clock, IdGenerator idGenerator) {
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.marcarLeidoPort = marcarLeidoPort;
        this.saveMensajePort = saveMensajePort;
        this.loadMensajePort = loadMensajePort;
        this.publicarMensajeFanoutPort = publicarMensajeFanoutPort;
        this.userSummaryFinder = userSummaryFinder;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public Mensaje enviar(EnviarMensajeCommand command) {
        requireActivo(command.actorId());
        requireParticipante(requireConversacion(command.conversacionId()), command.actorId());
        if (command.respuestaAId() != null) {
            requireRespuestaEnMismaConversacion(command.respuestaAId(), command.conversacionId());
        }

        Instant ahora = clock.now();
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
        Mensaje mensaje = Mensaje.escribir(MensajeId.of(idGenerator.newId()), command.conversacionId(),
                command.actorId(), command.tipo(), command.texto(), command.mediaBucket(), command.mediaRuta(),
                command.mediaMime(), command.mediaBytes(), command.mediaDuracionS(), command.respuestaAId(),
                ahora);
        Mensaje guardado = saveMensajePort.save(mensaje);
        // El emisor "ya leyo" hasta el mensaje que acaba de escribir.
        marcarLeidoPort.marcarLeido(command.conversacionId(), command.actorId(), ahora);
        publicarDespuesDelCommit(guardado);
        return guardado;
    }

    /**
     * Redis Pub/Sub solo empuja: el mensaje YA esta durable en Postgres antes de esta
     * llamada. Publicar antes del commit arriesgaria mostrar en vivo un mensaje que un
     * rollback despues borra (CLAUDE.MD del encargo: "SIEMPRE primero en Postgres").
     */
    private void publicarDespuesDelCommit(Mensaje mensaje) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publicarMensajeFanoutPort.publicar(mensaje);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publicarMensajeFanoutPort.publicar(mensaje);
            }
        });
    }

    /**
     * Se firma la subida ANTES de que el mensaje exista, asi que la autorizacion se repite aca
     * entera — activo, conversacion existente y participante — y no se delega a {@link #enviar}.
     * Sin esto, cualquiera con sesion podria firmar subidas contra el prefijo de una conversacion
     * de la que no forma parte, aunque despues no lograra enviar el mensaje.
     *
     * <p>Sin {@code @Transactional} a proposito: firmar es trabajo del adaptador de
     * almacenamiento y no debe correr con una conexion de Hikari retenida. Las tres
     * comprobaciones son lecturas y cada una va en la transaccion implicita de su repositorio.
     */
    @Override
    public UrlSubidaMediaChat solicitarUrl(SolicitarUrlSubidaMediaChatCommand command) {
        requireActivo(command.actorId());
        requireParticipante(requireConversacion(command.conversacionId()), command.actorId());
        String ruta = rutaDeMedia(command.conversacionId(), command.tipoContenido());
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlSubidaMediaChat(url, Mensaje.BUCKET_DEFAULT, ruta);
    }

    /**
     * Un prefijo por conversacion y, dentro, uno por tipo de archivo. El de la conversacion es lo
     * que permite borrar o caducar todo el material de un chat sin recorrer objeto por objeto; el
     * del tipo es el mismo criterio que ya usa el Muro (en S3 el prefijo es lo unico sobre lo que
     * se pueden aplicar reglas distintas de ciclo de vida o de lectura).
     *
     * <p>El tipo se rechaza aca porque el objeto se sube antes de que exista el mensaje: firmar
     * una subida que {@code Mensaje} despues va a rechazar deja el archivo huerfano en el bucket.
     */
    private static String rutaDeMedia(ConversacionId conversacionId, String tipoContenido) {
        String carpeta;
        if (tipoContenido.startsWith("image/")) {
            carpeta = "fotos";
        } else if (tipoContenido.startsWith("audio/")) {
            carpeta = "audios";
        } else if (tipoContenido.startsWith("video/")) {
            carpeta = "videos";
        } else {
            throw new IllegalArgumentException(
                    "tipoContenido debe empezar con image/, audio/ o video/: " + tipoContenido);
        }
        return "chat/" + conversacionId.value() + "/" + carpeta + "/" + UUID.randomUUID();
    }

    @Override
    public PaginaMensajes listar(UserId actorId, ConversacionId conversacionId, Instant cursor, int limite) {
        requireActivo(actorId);
        requireParticipante(requireConversacion(conversacionId), actorId);

        int limiteEfectivo = limite <= 0 ? LIMITE_POR_DEFECTO : Math.min(limite, LIMITE_MAXIMO);
        List<Mensaje> pagina = loadMensajePort.pagina(conversacionId, cursor, limiteEfectivo + 1);
        boolean hayMas = pagina.size() > limiteEfectivo;
        List<Mensaje> resultado = hayMas ? pagina.subList(0, limiteEfectivo) : pagina;
        Instant siguienteCursor = hayMas ? resultado.get(resultado.size() - 1).creadoEn() : null;

        List<MensajeEnriquecido> enriquecidos = enriquecer(resultado);
        return new PaginaMensajes(enriquecidos, siguienteCursor, hayMas);
    }

    /**
     * Resuelve nombre/avatar del emisor de cada mensaje y el preview de "respuesta a"
     * para TODA la pagina en, como mucho, DOS consultas EN LOTE — una a
     * {@code loadMensajePort.porIds} (mensajes originales citados) y una a
     * {@code userSummaryFinder.findByIds} (todos los emisores involucrados, propios y de
     * los originales) — nunca una consulta por mensaje (#29, mismo criterio que
     * {@code TracksDelDiaProyeccionService} de `habits`).
     */
    private List<MensajeEnriquecido> enriquecer(List<Mensaje> mensajes) {
        if (mensajes.isEmpty()) {
            return List.of();
        }
        List<MensajeId> idsRespuesta = mensajes.stream().map(Mensaje::respuestaAId).filter(Objects::nonNull)
                .distinct().toList();
        Map<MensajeId, Mensaje> originales = idsRespuesta.isEmpty() ? Map.of() : loadMensajePort.porIds(idsRespuesta);

        Set<UserId> idsUsuarios = new LinkedHashSet<>();
        mensajes.forEach(m -> idsUsuarios.add(m.emisorId()));
        originales.values().forEach(o -> idsUsuarios.add(o.emisorId()));
        Map<UserId, UserSummary> usuarios = userSummaryFinder.findByIds(idsUsuarios);

        return mensajes.stream().map(m -> aEnriquecido(m, originales, usuarios)).toList();
    }

    private MensajeEnriquecido aEnriquecido(Mensaje mensaje, Map<MensajeId, Mensaje> originales,
                                              Map<UserId, UserSummary> usuarios) {
        UserSummary emisor = usuarios.get(mensaje.emisorId());
        RespuestaPreview preview = mensaje.respuestaAId() == null ? null
                : previewDe(originales.get(mensaje.respuestaAId()), usuarios);
        return new MensajeEnriquecido(mensaje, emisor != null ? emisor.fullName() : null,
                emisor != null ? emisor.avatarUrl() : null, preview, urlDeLectura(mensaje));
    }

    /**
     * Deja de ser {@code static} a proposito: firmar necesita el puerto de almacenamiento. Se
     * firma por mensaje y no en lote porque {@code firmarSubida}/{@code firmarLectura} son calculo
     * local del SDK (no hay ida y vuelta a S3), asi que no es una consulta N+1.
     */
    private String urlDeLectura(Mensaje mensaje) {
        if (mensaje.mediaRuta() == null) {
            return null;
        }
        return almacenamientoPort.firmarLectura(mensaje.mediaRuta(), VALIDEZ_URL_LECTURA).toString();
    }

    /** {@code null} si el mensaje original ya no esta disponible (no deberia pasar hoy —
     * no hay borrado fisico — pero no hay razon para reventar el listado completo por
     * eso). */
    private static RespuestaPreview previewDe(Mensaje original, Map<UserId, UserSummary> usuarios) {
        if (original == null) {
            return null;
        }
        UserSummary emisorOriginal = usuarios.get(original.emisorId());
        return new RespuestaPreview(original.id(), emisorOriginal != null ? emisorOriginal.fullName() : null,
                original.tipo(), recortar(original.texto()), original.eliminadoEn());
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= MensajeEnriquecido.LARGO_PREVIEW ? limpio
                : limpio.substring(0, MensajeEnriquecido.LARGO_PREVIEW) + "…";
    }

    private void requireRespuestaEnMismaConversacion(MensajeId respuestaAId, ConversacionId conversacionId) {
        Mensaje original = loadMensajePort.porId(respuestaAId)
                .orElseThrow(() -> new NoSuchElementException("Mensaje no encontrado: " + respuestaAId));
        if (!original.conversacionId().equals(conversacionId)) {
            throw new IllegalArgumentException("No se puede responder a un mensaje de otra conversacion");
        }
    }

    /**
     * Autorizacion de una conversacion.
     *
     * <p>Para un grupo NO alcanza con {@code participantes_conversacion}: esa tabla es una
     * proyeccion, y una proyeccion vieja no se limita a mostrar de menos — concede acceso de
     * mas. Un mentor que roto el mes pasado conservaria su fila y con ella la puerta abierta al
     * chat de gente que ya no acompana. Por eso el grupo se revalida contra la pertenencia
     * vigente y la proyeccion queda para listar rapido (plan.md §6).
     *
     * <p>Los directos y el GLOBAL siguen con su politica de siempre: nadie pierde un DM porque
     * alguien roto.
     */
    private void requireParticipante(Conversacion conversacion, UserId usuarioId) {
        if (conversacion.tipo() == TipoConversacion.CELULA) {
            if (!pertenenciaVigentePort.perteneceAlGrupo(conversacion.celulaId(), usuarioId)) {
                throw new NotAuthorizedException("Tu asignacion cambio: ya no perteneces a ese grupo");
            }
            return;
        }
        if (!esParticipantePort.esParticipante(conversacion.id(), usuarioId)) {
            throw new NotAuthorizedException("No sos participante de esta conversacion");
        }
    }

    /** Devuelve la conversacion cargada: el guard de participacion la necesita para saber si es
     * de grupo, y volver a pedirla seria una consulta de mas por cada mensaje. */
    private Conversacion requireConversacion(ConversacionId id) {
        return loadConversacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + id));
    }

    private void requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
