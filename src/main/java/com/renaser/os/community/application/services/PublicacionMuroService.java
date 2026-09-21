package com.renaser.os.community.application.services;

import com.renaser.os.community.api.PublicacionCreadaEvent;
import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.community.api.PublicacionParaCompartir;
import com.renaser.os.community.api.PublicarEnMuroPort;
import com.renaser.os.community.api.PublicarEnMuroPort.PublicarDesdeEvidenciaComando;
import com.renaser.os.community.api.ReferenciasExternasDeMediaDelMuro;
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
import com.renaser.os.community.application.ports.out.publicacion.ReferenciasDeMediaDelMuroPort;
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
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PublicacionMuroService implements PublicarUseCase, EditarPublicacionUseCase, OcultarPublicacionUseCase,
        RestaurarPublicacionUseCase, EliminarPublicacionUseCase, ReaccionarUseCase, ConsultarFeedUseCase,
        ConsultarReaccionesUseCase, SolicitarUrlSubidaMediaUseCase, PublicarEnMuroPort, PublicacionMuroFinder {

    private static final Logger log = LoggerFactory.getLogger(PublicacionMuroService.class);

    private static final int TAMANO_PAGINA = 20;
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);
    /** Prefijo con el que {@link #rutaDeMedia} arma toda clave del Muro. */
    private static final String PREFIJO_MURO = "muro/";
    /** {@code muro/<carpeta>/<autorId>/<uuid>}: el id del autor va en la TERCERA parte. */
    private static final int PARTE_DEL_AUTOR = 2;
    private static final int PARTES_DE_UNA_CLAVE_DEL_MURO = 4;

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
    /**
     * Para sellar el dia de programa del autor en la publicacion (V51).
     *
     * <p>Se inyecta el contrato publico de `users` directamente, sin puerto propio, siguiendo lo
     * que esta clase ya hace con {@link UserSummaryFinder}: son las dos caras de lo mismo —datos
     * del autor que este modulo no es dueno de consultar por su cuenta.
     */
    private final ParticipacionProgramaFinder participacionFinder;
    /**
     * Quien mas, DENTRO de community, sigue mirando una clave {@code muro/} — otra publicacion o
     * un testimonio promovido. Ver {@link ReferenciasDeMediaDelMuroPort}.
     */
    private final ReferenciasDeMediaDelMuroPort referenciasDeMediaPort;
    /**
     * Lo mismo pero de los OTROS modulos, cada uno por su tabla — hoy {@code chat}, porque
     * compartir una publicacion no copia el archivo sino que referencia la misma clave. Es una
     * lista y no un puerto unico a proposito: ver
     * {@link ReferenciasExternasDeMediaDelMuro}.
     */
    private final List<ReferenciasExternasDeMediaDelMuro> referenciasExternas;

    public PublicacionMuroService(LoadPublicacionPort loadPublicacionPort, SavePublicacionPort savePublicacionPort,
                                   EliminarPublicacionPort eliminarPublicacionPort,
                                   LoadComentarioPort loadComentarioPort, ReaccionMuroPort reaccionMuroPort,
                                   ConsultarCategoriasMuroUseCase categoriasUseCase,
                                   ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort,
                                   AlmacenamientoPort almacenamientoPort, UserSummaryFinder userSummaryFinder,
                                   ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator,
                                   ParticipacionProgramaFinder participacionFinder,
                                   ReferenciasDeMediaDelMuroPort referenciasDeMediaPort,
                                   List<ReferenciasExternasDeMediaDelMuro> referenciasExternas) {
        this.referenciasDeMediaPort = referenciasDeMediaPort;
        this.referenciasExternas = List.copyOf(referenciasExternas);
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
        this.participacionFinder = participacionFinder;
    }

    @Override
    @Transactional
    public PublicacionVista publicar(PublicarCommand command) {
        requireActorPuedePublicar(command.autorId());
        if (command.categoriaClave() != null && !categoriasUseCase.clavesExistentes().contains(command.categoriaClave())) {
            throw new IllegalArgumentException("Categoria desconocida: " + command.categoriaClave());
        }
        List<MediaPublicacion> media = aMedia(command.media(), command.autorId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Publicacion publicacion = Publicacion.publicar(PublicacionId.of(idGenerator.newId()), command.autorId(),
                command.texto(), media, command.categoriaClave(), clock.now(), diaDePrograma(command.autorId()));
        Publicacion guardada = savePublicacionPort.save(publicacion);
        events.publishEvent(new PublicacionCreadaEvent(guardada.id().value(), guardada.autorId(),
                guardada.categoriaClave(), clock.now()));
        return aVista(guardada, command.autorId());
    }

    /**
     * Editar puede sacar una foto del carrusel, y eso tambien deja un objeto huerfano.
     *
     * <p>{@code save()} delega en {@code reemplazarMedia()}, que hace un {@code DELETE} de todas
     * las filas de {@code medias_publicacion} y reinserta la lista nueva: la media que sale pierde
     * ahi su unica fila y con ella la unica copia de su clave. El {@code @NotEmpty} de
     * {@code UpdateWallPostRequest} impide bajar a cero medias, no impide bajar de dos a una.
     *
     * <p><b>El orden importa en los dos extremos.</b> Las retiradas se calculan ANTES de mutar el
     * agregado (despues ya no estan en {@code publicacion.media()}), y el bucket se toca DESPUES
     * del {@code save} y despues de armar la vista: asi el censo no se cuenta a si mismo —las
     * filas viejas ya no existen— y no se destruye un objeto que una excepcion posterior fuera a
     * dejar todavia referenciado por la publicacion.
     */
    @Override
    @Transactional
    public PublicacionVista editar(EditarPublicacionCommand command) {
        Publicacion publicacion = requireVisible(command.publicacionId());
        requireActorHabilitado(command.actorId());
        if (!publicacion.autorId().equals(command.actorId())) {
            throw new NotAuthorizedException("No autorizado");
        }
        List<MediaPublicacion> nuevas = aMedia(command.media(), command.actorId());
        List<String> rutasQueSiguen = nuevas.stream().map(MediaPublicacion::ruta).toList();
        List<MediaPublicacion> retiradas = publicacion.media().stream()
                .filter(m -> !rutasQueSiguen.contains(m.ruta()))
                .toList();
        publicacion.editar(command.texto(), nuevas, clock.now());
        Publicacion guardada = savePublicacionPort.save(publicacion);
        PublicacionVista vista = aVista(guardada, command.actorId());
        retirarDelBucket(retiradas, command.publicacionId());
        return vista;
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

    /**
     * Borrado fisico, y fisico tiene que alcanzar al archivo.
     *
     * <p><b>El defecto que cierra.</b> Esto eran tres lineas de base de datos: el retorno de
     * {@link #requireOculta} —que ya viene con las medias y sus claves hidratadas
     * ({@code porId -> mediaDe})— se tiraba al piso una linea antes de que el
     * {@code ON DELETE CASCADE} de {@code medias_publicacion} se llevara {@code ruta_storage}
     * (V1:1097-1099), que es la UNICA columna del sistema donde vive la clave de una media del
     * Muro. Despues del {@code DELETE} el backend ya no podia limpiar el bucket ni queriendo: el
     * objeto quedaba vivo, seguia siendo re-firmable, y como {@code MediaItemRequest} solo mira el
     * prefijo, cualquiera que conservara la clave volvia a ponerlo en el feed sin subir nada.
     *
     * <p>El orden —objeto primero, fila despues— es el mismo de {@code EventoService.eliminar} y
     * {@code AccountDeletionService.purgeExpired}, y por la misma razon: cortado en el medio, la
     * fila sigue en pie y un reintento vuelve a nombrar el objeto ({@code borrar} es idempotente
     * por contrato del puerto). Al reves, un corte dejaria el objeto huerfano y ya sin nadie que
     * supiera nombrarlo, que es exactamente el defecto que este metodo viene a cerrar.
     */
    @Override
    @Transactional
    public void eliminarPermanente(EliminarPublicacionCommand command) {
        requireModerador(command.actorId());
        Publicacion publicacion = requireOculta(command.publicacionId());
        retirarDelBucket(publicacion.media(), command.publicacionId());
        eliminarPublicacionPort.eliminar(command.publicacionId());
    }

    /**
     * Saca del bucket los objetos que de verdad son SOLO de esta publicacion. Dos filtros, y
     * ninguno de los dos es cosmetico.
     *
     * <ol>
     *   <li><b>Prefijo {@code muro/}.</b> {@link #publicarDesdeEvidencia} guarda en
     *       {@code medias_publicacion} una clave {@code rocas/<autorId>/<rocaId>} que NO es del
     *       Muro: ese objeto es la evidencia del aprendiz y lo sigue referenciando
     *       {@code evidencias.ruta_storage} (V1:764), en otro modulo. Borrarlo con la publicacion
     *       destruiria el dato de otro.</li>
     *   <li><b>Sin referencias vivas en ningun lado.</b> Una clave {@code muro/} puede tener mas
     *       de un dueno y ninguno cae con la cascada: otra publicacion, un testimonio promovido
     *       ({@link ReferenciasDeMediaDelMuroPort}) o un mensaje que comparte la publicacion
     *       ({@link ReferenciasExternasDeMediaDelMuro}). Compartir al chat no copia el archivo:
     *       persiste la MISMA clave y la vuelve a firmar en cada lectura, y el borrado del chat es
     *       un tombstone. Un arreglo que solo mirara el prefijo dejaria esa foto en 404 para
     *       siempre en una conversacion privada que ninguna moderacion toco.</li>
     * </ol>
     *
     * <p><b>Fail-closed.</b> Si el censo falla, la excepcion sale y no se borra ni la fila ni el
     * objeto: la moderacion se reintenta con todo en pie, que es preferible a borrar a ciegas
     * (mismo criterio que {@code AccountDeletionService}). Lo que si se traga es un fallo de S3 —
     * no vale tumbar una moderacion por el bucket, como en {@code EventoService.eliminar}— pero
     * queda registrado: un objeto que sobrevive tiene que ser una linea de log, no un silencio.
     */
    private void retirarDelBucket(List<MediaPublicacion> media, PublicacionId publicacionId) {
        List<String> delMuro = media.stream()
                .map(MediaPublicacion::ruta)
                .filter(PublicacionMuroService::esClaveDelMuro)
                .distinct()
                .toList();
        if (delMuro.isEmpty()) {
            return;
        }
        Set<String> retenidas = referenciadasPorOtros(delMuro, publicacionId);
        for (String ruta : delMuro) {
            if (retenidas.contains(ruta)) {
                log.info("[community.PublicacionMuroService] la publicacion {} deja {} en el bucket: "
                        + "otra fila que le sobrevive lo referencia", publicacionId, ruta);
                continue;
            }
            try {
                almacenamientoPort.borrar(ruta);
            } catch (RuntimeException e) {
                log.error("[community.PublicacionMuroService] la publicacion {} se borra pero quedo el objeto {}",
                        publicacionId, ruta, e);
            }
        }
    }

    /** El censo completo: las tablas de community mas lo que declare cada modulo por su cuenta. */
    private Set<String> referenciadasPorOtros(List<String> rutas, PublicacionId publicacionId) {
        Set<String> retenidas = new HashSet<>(referenciasDeMediaPort.referenciadasFueraDe(rutas, publicacionId));
        for (ReferenciasExternasDeMediaDelMuro censo : referenciasExternas) {
            retenidas.addAll(censo.referenciadas(rutas));
        }
        return retenidas;
    }

    private static boolean esClaveDelMuro(String ruta) {
        return ruta != null && ruta.startsWith(PREFIJO_MURO);
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

    /**
     * SIN {@code requireActorActivo}, a diferencia de sus vecinos, y es deliberado: los otros
     * metodos le DEVUELVEN contenido del Muro a un actor (nombres de terceros, feed), asi que
     * chequean que ese actor pueda ver. Este responde por un hecho del propio
     * {@code autorId} — un booleano, sin datos de nadie mas — y su llamador
     * ({@code habits.RegistroService}) ya autorizo al aprendiz contra si mismo y contra su
     * estado de cuenta antes de llegar aca. Agregar el guard seria consultar `usuarios` una
     * segunda vez en el camino de completar un habito para reconfirmar lo mismo.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean publicoEntre(UserId autorId, Instant desde, Instant hasta) {
        return loadPublicacionPort.existeDeAutorEntre(autorId, desde, hasta);
    }

    /**
     * Sin {@code requireActorActivo}: a diferencia de {@link #ultimoAutor}, esto NO expone datos de
     * otra persona al cliente — devuelve una referencia de S3 que viaja de modulo a modulo y nunca
     * sale al telefono. Quien llama (`chat`) ya autorizo a su actor como participante de la
     * conversacion donde va a compartir.
     *
     * <p>Se toma la PRIMERA media de la publicacion. Una publicacion admite hasta 10 (MEDIA_MAX) y
     * un mensaje de chat, una sola: compartir manda la primera, que es la que el Muro muestra como
     * portada. Mandar las diez serian diez mensajes, y eso es una decision de producto que nadie
     * pidio.
     */
    @Override
    public Optional<PublicacionParaCompartir> paraCompartir(UUID publicacionId) {
        return loadPublicacionPort.porId(PublicacionId.of(publicacionId))
                /* La puerta de visibilidad, que faltaba (2026-09-18). Este era el UNICO read del
                   servicio que no la cruzaba: sus cinco hermanos usan `requireVisible`/`requireOculta`.
                   Ocultar es lo que hace DELETE /api/v1/wall/{id} —"borrar mi publicacion" desde la
                   app— y tambien lo que hace un moderador. Sin este filtro, compartir al chat
                   RESUCITABA el texto y la foto de una publicacion ya borrada o retirada, y
                   `MensajeService.urlDeLectura` prefirmaba esa media para todo participante de la
                   conversacion destino; con el auto-join de la GLOBAL, "todos". El javadoc de abajo
                   decia que esto "nunca sale al telefono" — dejo de ser cierto cuando se cableo
                   compartir al chat. */
                .filter(publicacion -> !publicacion.oculta())
                .map(publicacion -> {
                    MediaPublicacion portada = publicacion.media().isEmpty() ? null : publicacion.media().get(0);
                    return new PublicacionParaCompartir(
                            publicacion.id().value(),
                            publicacion.autorId(),
                            publicacion.texto(),
                            portada == null ? null : portada.bucket(),
                            portada == null ? null : portada.ruta(),
                            portada == null ? null : portada.mime());
                });
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
        /* La segunda puerta al Muro, que el arreglo del 2026-09-18 no cubria.
           `MediaItemRequest.exigirClaveDelMuro` vive en el DTO REST, asi que solo protege
           `POST /wall` y `PATCH /wall/{id}`. Este camino —completar una roca con
           `publishedToWall=true`— entra por el puerto y llegaba sin ninguna comprobacion: la ruta
           del cliente se guardaba tal cual y `aVista` la firma para CADA lector del feed, que es
           global. O sea que la firma del Pacto de Sangre de una persona se repartia, firmada, a
           todo el padron. Peor que el bug original, donde la fuga iba solo al atacante.

           Se comprueba ACA y no en el constructor de `MediaPublicacion`: ese constructor tambien
           corre al LEER de la base, asi que un check duro ahi romperia el feed para las
           publicaciones de rocas ya persistidas. */
        exigirRutaDelAutor(comando.ruta(), comando.autorId());
        MediaPublicacion media = new MediaPublicacion(comando.bucket(), comando.ruta(), comando.mime(), 0);
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Publicacion publicacion = Publicacion.publicarAutomatica(PublicacionId.of(idGenerator.newId()),
                comando.autorId(), comando.texto(), List.of(media), clock.now(), diaDePrograma(comando.autorId()));
        Publicacion guardada = savePublicacionPort.save(publicacion);
        events.publishEvent(new PublicacionCreadaEvent(guardada.id().value(), guardada.autorId(),
                guardada.categoriaClave(), clock.now()));
        return guardada.id().value();
    }

    /**
     * El dia de programa del autor AHORA, para dejarlo sellado en la publicacion.
     *
     * <p>Devuelve {@code null} —y no 0— en los tres casos en que no hay dia que contar: el autor no
     * esta inscrito, no activo el programa todavia, o su dia cae fuera de 1..90. {@code null} dice
     * "no corresponde"; el 0 era exactamente la mentira que el Muro venia mostrando.
     *
     * <p>Que esto falle no puede impedir publicar: una consulta caida no vale una publicacion
     * perdida. Se degrada a {@code null}, que la insignia sabe no dibujar.
     */
    private Integer diaDePrograma(UserId autorId) {
        try {
            return participacionFinder.deParticipante(autorId)
                    .filter(ParticipacionPrograma::inscrito)
                    .filter(ParticipacionPrograma::activado)
                    .map(ParticipacionPrograma::diaPrograma)
                    .filter(dia -> dia >= 1 && dia <= 90)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
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

    private List<MediaPublicacion> aMedia(List<ArchivoEntrada> entradas, UserId autorId) {
        List<MediaPublicacion> media = new ArrayList<>();
        for (int i = 0; i < entradas.size(); i++) {
            ArchivoEntrada e = entradas.get(i);
            String bucket = (e.bucket() == null || e.bucket().isBlank()) ? MediaPublicacion.BUCKET_DEFAULT : e.bucket();
            media.add(new MediaPublicacion(bucket, exigirClaveDelMuroDelAutor(e.ruta(), autorId), e.mime(), i));
        }
        return media;
    }

    /**
     * La clave de una media tiene que ser del muro Y del propio autor.
     *
     * <p>{@code MediaItemRequest.exigirClaveDelMuro} ya exigia el prefijo, y su javadoc declaraba
     * que dentro de {@code muro/} no se acotaba por autor "porque esos archivos ya son visibles en
     * el feed para todo el grupo". Esa suposicion deja de valer en el instante en que una
     * publicacion se borra: ahi el objeto ya no es visible para nadie, pero su clave sigue en manos
     * de quien la haya guardado —el autor la recibe en el campo {@code ruta} de
     * {@code /wall/media/upload-url}, y cualquier lector del feed la recibe dentro del path de la
     * URL prefirmada—. Sin este guard, una clave suelta se recicla en una publicacion nueva; y,
     * peor, publicar la clave de la foto de OTRO convierte esa publicacion en una referencia viva
     * que impide para siempre que la moderacion retire el objeto del original.
     *
     * <p><b>Vive aca y no en el DTO REST a proposito.</b> Es donde ya vive
     * {@link #exigirRutaDelAutor} para {@code rocas/}, que es el mismo criterio, y sobre todo:
     * {@code MediaItemRequest} solo protege {@code POST /wall} y {@code PATCH /wall/{id}} — esa
     * fue justamente la rendija que dejo abierta el arreglo del 2026-09-18 y que
     * {@link #publicarDesdeEvidencia} tuvo que tapar aparte. En el caso de uso cubre cualquier
     * entrada, presente o futura.
     *
     * <p>No se comprueba en el constructor de {@link MediaPublicacion} porque ese constructor
     * tambien corre al LEER de la base: un check duro ahi romperia el feed para las publicaciones
     * ya persistidas con claves viejas.
     *
     * <p>El servidor genera siempre {@code muro/<carpeta>/<autorId>/<uuid>}
     * ({@link #rutaDeMedia}), asi que esto no rechaza nada que el propio sistema haya emitido. En
     * los dos llamadores el actor ES el autor: publicar crea a nombre del actor, y editar ya exige
     * {@code publicacion.autorId().equals(command.actorId())}.
     */
    private static String exigirClaveDelMuroDelAutor(String ruta, UserId autorId) {
        if (esClaveDelMuro(ruta)) {
            String[] partes = ruta.split("/");
            if (partes.length >= PARTES_DE_UNA_CLAVE_DEL_MURO
                    && partes[PARTE_DEL_AUTOR].toLowerCase(Locale.ROOT)
                            .equals(autorId.value().toString().toLowerCase(Locale.ROOT))) {
                return ruta;
            }
        }
        throw new NotAuthorizedException("La media de una publicacion tiene que ser un objeto propio del muro ("
                + PREFIJO_MURO + "<carpeta>/" + autorId.value() + "/<uuid>)");
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

    /**
     * La evidencia publicada tiene que ser un archivo del propio autor.
     *
     * <p>El prefijo es el que emite {@code RocaDiariaService.solicitarUrl}:
     * {@code rocas/<autorId>/<rocaId>}. No rechaza nada que el sistema haya firmado.
     *
     * <p>El {@code bucket} del comando no sirve para decidir: {@code firmarLectura(ruta, validez)}
     * ni siquiera lo recibe y el adaptador usa siempre el bucket de configuracion — hay uno solo
     * fisico para todos los modulos. Por eso la unica frontera real es el prefijo de la CLAVE.
     */
    private static void exigirRutaDelAutor(String ruta, UserId autorId) {
        String esperado = "rocas/" + autorId.value() + "/";
        if (ruta == null || !ruta.startsWith(esperado)) {
            throw new NotAuthorizedException(
                    "La evidencia publicada tiene que ser un archivo propio (" + esperado + ")");
        }
    }
}
