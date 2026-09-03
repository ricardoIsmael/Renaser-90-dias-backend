package com.renaser.os.community.application.services;

import com.renaser.os.community.api.PublicacionCreadaEvent;
import com.renaser.os.community.api.PublicarEnMuroPort;
import com.renaser.os.community.api.PublicarEnMuroPort.PublicarDesdeEvidenciaComando;
import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase.ReaccionVista;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase;
import com.renaser.os.community.application.ports.out.publicacion.EliminarPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadComentarioPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.ReaccionMuroPort;
import com.renaser.os.community.application.ports.out.publicacion.SavePublicacionPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro.Quitar;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro.Reaccionar;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicacionMuroService implements PublicarUseCase, EditarPublicacionUseCase, OcultarPublicacionUseCase,
        RestaurarPublicacionUseCase, EliminarPublicacionUseCase, ReaccionarUseCase, ConsultarFeedUseCase,
        ConsultarReaccionesUseCase, SolicitarUrlSubidaMediaUseCase, PublicarEnMuroPort {

    private static final int TAMANO_PAGINA = 20;
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final LoadPublicacionPort loadPublicacionPort;
    private final SavePublicacionPort savePublicacionPort;
    private final EliminarPublicacionPort eliminarPublicacionPort;
    private final LoadComentarioPort loadComentarioPort;
    private final ReaccionMuroPort reaccionMuroPort;
    private final ConsultarCategoriasMuroUseCase categoriasUseCase;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public PublicacionMuroService(LoadPublicacionPort loadPublicacionPort, SavePublicacionPort savePublicacionPort,
                                   EliminarPublicacionPort eliminarPublicacionPort,
                                   LoadComentarioPort loadComentarioPort, ReaccionMuroPort reaccionMuroPort,
                                   ConsultarCategoriasMuroUseCase categoriasUseCase,
                                   ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort,
                                   AlmacenamientoPort almacenamientoPort, UserSummaryFinder userSummaryFinder,
                                   ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator) {
        this.loadPublicacionPort = loadPublicacionPort;
        this.savePublicacionPort = savePublicacionPort;
        this.eliminarPublicacionPort = eliminarPublicacionPort;
        this.loadComentarioPort = loadComentarioPort;
        this.reaccionMuroPort = reaccionMuroPort;
        this.categoriasUseCase = categoriasUseCase;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.almacenamientoPort = almacenamientoPort;
        this.userSummaryFinder = userSummaryFinder;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public PublicacionVista publicar(PublicarCommand command) {
        requireActorPuedePublicar(command.autorId());
        if (command.categoriaClave() != null && !categoriasUseCase.clavesExistentes().contains(command.categoriaClave())) {
            throw new IllegalArgumentException("Categoria desconocida: " + command.categoriaClave());
        }
        List<MediaPublicacion> media = aMedia(command.media());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Publicacion publicacion = Publicacion.publicar(PublicacionId.of(idGenerator.newId()), command.autorId(),
                command.texto(), media, command.categoriaClave(), clock.now());
        Publicacion guardada = savePublicacionPort.save(publicacion);
        events.publishEvent(new PublicacionCreadaEvent(guardada.id().value(), guardada.autorId(),
                guardada.categoriaClave(), clock.now()));
        return aVista(guardada, command.autorId());
    }

    @Override
    @Transactional
    public PublicacionVista editar(EditarPublicacionCommand command) {
        Publicacion publicacion = requireVisible(command.publicacionId());
        requireActorHabilitado(command.actorId());
        if (!publicacion.autorId().equals(command.actorId())) {
            throw new NotAuthorizedException("No autorizado");
        }
        publicacion.editar(command.texto(), aMedia(command.media()), clock.now());
        Publicacion guardada = savePublicacionPort.save(publicacion);
        return aVista(guardada, command.actorId());
    }

    @Override
    @Transactional
    public void ocultar(OcultarPublicacionCommand command) {
        Publicacion publicacion = requireVisible(command.publicacionId());
        requireActorHabilitado(command.actorId());
        boolean puedeModerar = esModerador(command.actorId());
        if (!publicacion.autorId().equals(command.actorId()) && !puedeModerar) {
            throw new NotAuthorizedException("No autorizado");
        }
        publicacion.ocultar(clock.now());
        savePublicacionPort.save(publicacion);
    }

    @Override
    @Transactional
    public void restaurar(RestaurarPublicacionCommand command) {
        requireModerador(command.actorId());
        Publicacion publicacion = requireOculta(command.publicacionId());
        publicacion.restaurar(clock.now());
        savePublicacionPort.save(publicacion);
    }

    @Override
    @Transactional
    public void eliminarPermanente(EliminarPublicacionCommand command) {
        requireModerador(command.actorId());
        requireOculta(command.publicacionId());
        eliminarPublicacionPort.eliminar(command.publicacionId());
    }

    @Override
    @Transactional
    public ResultadoReaccion reaccionar(ReaccionarCommand command) {
        requireVisible(command.publicacionId());
        requireActorHabilitado(command.actorId());
        TipoReaccion existente = reaccionMuroPort.deUsuario(command.publicacionId(), command.actorId()).orElse(null);
        ReaccionMuro.ResultadoToggle resultado = ReaccionMuro.calcularToggle(existente, command.tipo());
        boolean reaccionado;
        if (resultado instanceof Quitar) {
            reaccionMuroPort.eliminar(command.publicacionId(), command.actorId());
            reaccionado = false;
        } else if (resultado instanceof Reaccionar reaccionar) {
            reaccionMuroPort.upsert(command.publicacionId(), command.actorId(), reaccionar.tipo());
            reaccionado = true;
        } else {
            throw new IllegalStateException("Resultado de toggle desconocido: " + resultado);
        }
        Map<TipoReaccion, Integer> conteo = reaccionMuroPort.contarPorTipo(command.publicacionId());
        return new ResultadoReaccion(reaccionado, conteo.getOrDefault(TipoReaccion.ME_GUSTA, 0),
                conteo.getOrDefault(TipoReaccion.NO_ME_GUSTA, 0));
    }

    /**
     * Quien reacciono a una publicacion (modal "Reacciones del post"). Misma puerta de
     * visibilidad que {@link #reaccionar}: {@link #requireVisible} primero (una publicacion
     * oculta o inexistente es 404 para cualquiera), {@link #requireActorHabilitado} despues
     * (actor inexistente o suspendido es 403 fail-closed, nunca delata si el recurso existe).
     *
     * <p>Nunca N+1: la lista completa de reacciones sale de UNA consulta
     * ({@link ReaccionMuroPort#listarDe}) y los datos de las personas de UNA sola pasada en
     * lote ({@link UserSummaryFinder#findByIds}), sin importar cuantas reacciones tenga la
     * publicacion — mismo criterio que {@link #aVistas} para el feed (E-80).
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReaccionVista> reacciones(UserId actorId, PublicacionId publicacionId) {
        requireVisible(publicacionId);
        requireActorHabilitado(actorId);
        List<ReaccionMuro> filas = reaccionMuroPort.listarDe(publicacionId);
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<UserId, UserSummary> usuarios = userSummaryFinder.findByIds(
                filas.stream().map(ReaccionMuro::usuarioId).distinct().toList());
        return filas.stream().map(fila -> aReaccionVista(fila, usuarios.get(fila.usuarioId()))).toList();
    }

    private static ReaccionVista aReaccionVista(ReaccionMuro fila, UserSummary usuario) {
        return new ReaccionVista(fila.usuarioId(), usuario != null ? usuario.fullName() : null,
                usuario != null ? usuario.avatarUrl() : null, usuario != null ? usuario.role() : null, fila.tipo());
    }

    /**
     * {@code readOnly} y no una transaccion normal: las cinco consultas de una carga del Muro
     * comparten UNA conexion de Hikari en vez de pedir y devolver una cada una (E-80), y el
     * `readOnly` le dice a Hibernate que no haga dirty checking de nada de lo que lea.
     *
     * <p>Firmar las URLs de lectura dentro de la transaccion es seguro y no contradice la regla
     * de CLAUDE.MD sec. 7 sobre no esperar a un servicio externo con una transaccion abierta: el
     * presigner de S3 calcula la firma <b>localmente</b> con la credencial, sin llamar a AWS
     * (ver {@code AlmacenamientoS3Config.s3Presigner}). No hay espera de red que pueda retener
     * la conexion.
     */
    @Override
    @Transactional(readOnly = true)
    public PaginaPublicaciones feed(UserId actorId, Instant cursor, String categoriaClave) {
        requireActorActivo(actorId);
        if (categoriaClave != null && !categoriasUseCase.clavesExistentes().contains(categoriaClave)) {
            throw new IllegalArgumentException("Categoria desconocida: " + categoriaClave);
        }
        List<Publicacion> pagina = loadPublicacionPort.feed(cursor, TAMANO_PAGINA, categoriaClave);
        return aPagina(pagina, actorId);
    }

    /** Misma razon que {@link #feed}: una conexion para toda la pagina, no una por consulta. */
    @Override
    @Transactional(readOnly = true)
    public PaginaPublicaciones feedOculto(UserId actorId, Instant cursor) {
        requireModerador(actorId);
        List<Publicacion> pagina = loadPublicacionPort.feedOculto(cursor, TAMANO_PAGINA);
        return aPagina(pagina, actorId);
    }

    @Override
    public int contarMisPublicaciones(UserId actorId) {
        requireActorActivo(actorId);
        return loadPublicacionPort.contarPorAutor(actorId);
    }

    /** Mismo guard que {@link #feed}: expone el nombre completo de otra persona, asi que
     * una cuenta suspendida (o inexistente) no lo obtiene. Ver E-50. */
    @Override
    public Optional<String> ultimoAutor(UserId actorId) {
        requireActorActivo(actorId);
        return loadPublicacionPort.ultimaVisible()
                .flatMap(p -> consultarPerfilUsuarioPort.porId(p.autorId()))
                .map(PerfilUsuario::nombreCompleto);
    }

    @Override
    public UrlSubidaMedia solicitarUrl(SolicitarUrlSubidaMediaCommand command) {
        requireActorPuedePublicar(command.actorId());
        String ruta = rutaDeMedia(command.tipoContenido(), command.actorId());
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlSubidaMedia(url, MediaPublicacion.BUCKET_DEFAULT, ruta);
    }

    /**
     * Fotos y videos van a prefijos separados dentro de {@code muro/}. No es orden: en S3 el
     * prefijo es lo unico que permite aplicar reglas distintas por tipo de archivo — ciclo de
     * vida, clase de almacenamiento, o un permiso de lectura que valga para las fotos y no para
     * los videos. Mezclarlos en una sola carpeta obliga a mirar la extension de cada objeto para
     * decidir cualquiera de esas cosas.
     *
     * <p>El tipo se rechaza aca ademas de en {@link MediaPublicacion}: esta URL se firma ANTES de
     * que exista la publicacion, asi que si no se valida en este punto se firma una subida para
     * un archivo que el dominio va a rechazar despues, y el objeto queda huerfano en el bucket.
     */
    private static String rutaDeMedia(String tipoContenido, UserId autorId) {
        String carpeta;
        if (tipoContenido.startsWith("image/")) {
            carpeta = "fotos";
        } else if (tipoContenido.startsWith("video/")) {
            carpeta = "videos";
        } else {
            throw new IllegalArgumentException(
                    "tipoContenido debe empezar con image/ o video/: " + tipoContenido);
        }
        return "muro/" + carpeta + "/" + autorId + "/" + UUID.randomUUID();
    }

    /** Hueco #17 (docs/MODULO_ROCKS.md sec. 11.2): entrada publica para que OTRO modulo
     * (hoy `rocks`) cree una publicacion real en el Muro a partir de una evidencia ya
     * subida. Mismas reglas de autorizacion que {@code publicar()} — no hay bypass por
     * venir de otro modulo. */
    @Override
    @Transactional
    public UUID publicarDesdeEvidencia(PublicarDesdeEvidenciaComando comando) {
        requireActorPuedePublicar(comando.autorId());
        MediaPublicacion media = new MediaPublicacion(comando.bucket(), comando.ruta(), comando.mime(), 0);
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Publicacion publicacion = Publicacion.publicarAutomatica(PublicacionId.of(idGenerator.newId()),
                comando.autorId(), comando.texto(), List.of(media), clock.now());
        Publicacion guardada = savePublicacionPort.save(publicacion);
        events.publishEvent(new PublicacionCreadaEvent(guardada.id().value(), guardada.autorId(),
                guardada.categoriaClave(), clock.now()));
        return guardada.id().value();
    }

    private PaginaPublicaciones aPagina(List<Publicacion> filasConExtra, UserId actorId) {
        boolean hayMas = filasConExtra.size() > TAMANO_PAGINA;
        List<Publicacion> pagina = hayMas ? filasConExtra.subList(0, TAMANO_PAGINA) : filasConExtra;
        Instant siguiente = hayMas ? pagina.get(pagina.size() - 1).creadoEn() : null;
        return new PaginaPublicaciones(aVistas(pagina, actorId), siguiente);
    }

    private PublicacionVista aVista(Publicacion publicacion, UserId viewerId) {
        return aVistas(List.of(publicacion), viewerId).get(0);
    }

    /**
     * Enriquece una pagina entera con <b>cuatro consultas fijas</b>, no cuatro por publicacion
     * (E-80). Antes esto llamaba a {@code aVista} en un bucle: con {@code TAMANO_PAGINA = 20} eso
     * eran ~84 consultas por carga del Muro, y como {@code feed()} no abria transaccion, cada una
     * pedia y devolvia su propia conexion de Hikari (pool de 20). En el Postgres local no se nota
     * -- esta a microsegundos-- pero contra una base administrada en otra zona son 0,5-2 ms por
     * viaje, o sea 40-170 ms de pura espera por usuario y por carga, multiplicado por cada
     * aprendiz que abre la Comunidad.
     *
     * <p>El costo ahora no depende del tamano de la pagina: 20 publicaciones cuestan lo mismo que
     * 1. Firmar las URLs si es por archivo, pero eso es calculo local (HMAC), no una vuelta a AWS
     * -- ver el javadoc de {@code AlmacenamientoS3Config.s3Presigner}.
     */
    private List<PublicacionVista> aVistas(List<Publicacion> publicaciones, UserId viewerId) {
        if (publicaciones.isEmpty()) {
            return List.of();
        }
        List<PublicacionId> ids = publicaciones.stream().map(Publicacion::id).toList();
        Map<UserId, PerfilUsuario> autores = consultarPerfilUsuarioPort.porIds(
                publicaciones.stream().map(Publicacion::autorId).distinct().toList());
        Map<PublicacionId, Map<TipoReaccion, Integer>> conteos = reaccionMuroPort.contarPorTipoDeVarias(ids);
        Map<PublicacionId, TipoReaccion> misReacciones = reaccionMuroPort.deUsuarioEnVarias(ids, viewerId);
        Map<PublicacionId, Integer> comentarios = loadComentarioPort.contarDeVarias(ids);
        return publicaciones.stream()
                .map(p -> aVista(p, autores.get(p.autorId()), conteos.getOrDefault(p.id(), Map.of()),
                        misReacciones.get(p.id()), comentarios.getOrDefault(p.id(), 0)))
                .toList();
    }

    private PublicacionVista aVista(Publicacion publicacion, PerfilUsuario autor,
                                     Map<TipoReaccion, Integer> conteo, TipoReaccion miReaccion, int comentarios) {
        List<MediaFirmada> media = publicacion.media().stream()
                .map(m -> new MediaFirmada(almacenamientoPort.firmarLectura(m.ruta(), VALIDEZ_URL_LECTURA), m.mime(),
                        m.orden()))
                .toList();
        return new PublicacionVista(publicacion, autor != null ? autor.nombreCompleto() : null,
                autor != null ? autor.avatarUrl() : null, conteo.getOrDefault(TipoReaccion.ME_GUSTA, 0),
                conteo.getOrDefault(TipoReaccion.NO_ME_GUSTA, 0), miReaccion, comentarios, media);
    }

    private List<MediaPublicacion> aMedia(List<ArchivoEntrada> entradas) {
        List<MediaPublicacion> media = new ArrayList<>();
        for (int i = 0; i < entradas.size(); i++) {
            ArchivoEntrada e = entradas.get(i);
            String bucket = (e.bucket() == null || e.bucket().isBlank()) ? MediaPublicacion.BUCKET_DEFAULT : e.bucket();
            media.add(new MediaPublicacion(bucket, e.ruta(), e.mime(), i));
        }
        return media;
    }

    private Publicacion requireVisible(PublicacionId id) {
        Publicacion publicacion = loadPublicacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Publicacion no encontrada: " + id));
        if (publicacion.oculta()) {
            throw new NoSuchElementException("Publicacion no encontrada: " + id);
        }
        return publicacion;
    }

    /** Restaurar y el borrado fisico solo actuan sobre la cola de moderacion — una
     * publicacion visible "no existe" para esas dos operaciones (wall/service.ts:189-190,
     * 203-204: {@code if (!post || !post.hidden) return 404}). */
    private Publicacion requireOculta(PublicacionId id) {
        Publicacion publicacion = loadPublicacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Publicacion no encontrada: " + id));
        if (!publicacion.oculta()) {
            throw new NoSuchElementException("Publicacion no encontrada: " + id);
        }
        return publicacion;
    }

    private void requireActorPuedePublicar(UserId actorId) {
        UserSummary actor = requireActorActivo(actorId);
        if (actor.role() != UserRole.TRAINEE && actor.role() != UserRole.MENTOR
                && actor.role() != UserRole.MENTOR_LEAD && actor.role() != UserRole.ADMIN
                && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Rol sin permiso para publicar en el Muro");
        }
    }

    /** Predicado puro — mismo criterio que {@code ComentarioMuroService.esModerador}: un
     * actor inexistente no es moderador, no es un 404 (fail-closed a 403, no a "no
     * encontrado" que filtraria si la publicacion existe). Ver docs/MODULO_COMMUNITY.md
     * sec. 5. */
    private boolean esModerador(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .map(actor -> actor.status() == UserStatus.ACTIVE
                        && (actor.role() == UserRole.ADMIN || actor.role() == UserRole.ALCHEMIST))
                .orElse(false);
    }

    private void requireModerador(UserId actorId) {
        if (!esModerador(actorId)) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST moderan el Muro");
        }
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }

    /** Fail-closed, mismo criterio que {@code esModerador}: actor inexistente o suspendido
     * -> false, nunca una excepcion de tipo distinto. Se usa DESPUES de confirmar que el
     * recurso es visible (a diferencia de {@code requireActorActivo}, que se usa donde no
     * hay un recurso previo que filtrar) — asi cualquier fallo de actor cae siempre a 403,
     * nunca a un 404 con mensaje distinto que delataria, por comparacion, que el recurso SI
     * existe (auditoria E2E adversarial; mismo motivo que el fail-closed de esModerador). */
    private boolean actorActivo(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .map(actor -> actor.status() == UserStatus.ACTIVE)
                .orElse(false);
    }

    private void requireActorHabilitado(UserId actorId) {
        if (!actorActivo(actorId)) {
            throw new NotAuthorizedException("Cuenta inexistente o suspendida");
        }
    }
}
