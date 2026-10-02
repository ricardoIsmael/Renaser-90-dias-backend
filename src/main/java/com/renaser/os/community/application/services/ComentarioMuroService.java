package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase;
import com.renaser.os.community.application.ports.out.publicacion.LoadComentarioPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.SaveComentarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ComentarioMuroService implements EscribirComentarioUseCase, EditarComentarioUseCase,
        OcultarComentarioUseCase, ConsultarComentariosUseCase {

    private static final int TAMANO_PAGINA = 30;

    private final LoadPublicacionPort loadPublicacionPort;
    private final LoadComentarioPort loadComentarioPort;
    private final SaveComentarioPort saveComentarioPort;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;
    /** D-243: los comentarios de cuentas cerradas esperando su borrado no se muestran ni cuentan. */
    private final SinCuentasCerradas sinCerradas;

    public ComentarioMuroService(LoadPublicacionPort loadPublicacionPort, LoadComentarioPort loadComentarioPort,
                                  SaveComentarioPort saveComentarioPort,
                                  ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort,
                                  UserSummaryFinder userSummaryFinder, Clock clock, IdGenerator idGenerator,
                                  CuentasCerradasFinder cuentasCerradas) {
        this.sinCerradas = new SinCuentasCerradas(cuentasCerradas);
        this.loadPublicacionPort = loadPublicacionPort;
        this.loadComentarioPort = loadComentarioPort;
        this.saveComentarioPort = saveComentarioPort;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public EscribirComentarioUseCase.Resultado escribir(EscribirComentarioCommand command) {
        requireVisible(command.publicacionId());
        requireActorHabilitado(command.autorId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Comentario comentario = Comentario.escribir(ComentarioId.of(idGenerator.newId()), command.publicacionId(),
                command.autorId(), command.texto(), clock.now());
        Comentario guardado = saveComentarioPort.save(comentario);
        int cantidad = totalVisible(command.publicacionId());
        return new EscribirComentarioUseCase.Resultado(aVista(guardado), cantidad);
    }

    @Override
    @Transactional
    public ComentarioVista editar(EditarComentarioCommand command) {
        Comentario comentario = requireComentarioVisible(command.comentarioId());
        requireActorHabilitado(command.actorId());
        if (!comentario.autorId().equals(command.actorId())) {
            throw new NotAuthorizedException("No autorizado");
        }
        comentario.editar(command.texto(), clock.now());
        return aVista(saveComentarioPort.save(comentario));
    }

    @Override
    @Transactional
    public OcultarComentarioUseCase.Resultado ocultar(OcultarComentarioCommand command) {
        Comentario comentario = requireComentarioVisible(command.comentarioId());
        requireActorHabilitado(command.actorId());
        boolean puedeModerar = esModerador(command.actorId());
        if (!comentario.autorId().equals(command.actorId()) && !puedeModerar) {
            throw new NotAuthorizedException("No autorizado");
        }
        comentario.ocultar(clock.now());
        saveComentarioPort.save(comentario);
        return new OcultarComentarioUseCase.Resultado(totalVisible(comentario.publicacionId()));
    }

    @Override
    public PaginaComentarios pagina(PublicacionId publicacionId, Instant cursor) {
        requireVisible(publicacionId);
        List<Comentario> filas = loadComentarioPort.pagina(publicacionId, cursor, TAMANO_PAGINA);
        boolean hayMas = filas.size() > TAMANO_PAGINA;
        List<Comentario> cruda = hayMas ? filas.subList(0, TAMANO_PAGINA) : filas;
        int total = totalVisible(publicacionId);
        // El cursor sale de la página CRUDA (D-243): la siguiente arranca donde terminó esta aunque su
        // último comentario sea de una cuenta cerrada y no se muestre. Ver `PublicacionMuroService.feed`.
        Instant siguiente = hayMas ? cruda.get(cruda.size() - 1).creadoEn() : null;
        List<Comentario> pagina = sinCerradas.de(cruda, Comentario::autorId);
        // Los autores de toda la pagina en UNA consulta (V-6, D-180). Antes era una por comentario:
        // 30 comentarios, 30 consultas a `usuarios`.
        Map<UserId, PerfilUsuario> autores = consultarPerfilUsuarioPort.porIds(
                pagina.stream().map(Comentario::autorId).collect(Collectors.toSet()));
        return new PaginaComentarios(pagina.stream().map(c -> aVista(c, autores.get(c.autorId()))).toList(),
                siguiente, total);
    }

    /** Comentarios visibles sin los de cuentas cerradas (D-243): el número y la lista dicen lo mismo. */
    private int totalVisible(PublicacionId publicacionId) {
        Map<UserId, Integer> porAutor = loadComentarioPort.contarPorAutor(publicacionId);
        Set<UserId> cerradas = sinCerradas.cerradasEntre(List.copyOf(porAutor.keySet()), autor -> autor);
        return porAutor.entrySet().stream()
                .filter(e -> !cerradas.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private ComentarioVista aVista(Comentario comentario) {
        return aVista(comentario, consultarPerfilUsuarioPort.porId(comentario.autorId()).orElse(null));
    }

    private static ComentarioVista aVista(Comentario comentario, PerfilUsuario autor) {
        return new ComentarioVista(comentario, autor != null ? autor.nombreCompleto() : null,
                autor != null ? autor.avatarUrl() : null);
    }

    private Publicacion requireVisible(PublicacionId publicacionId) {
        Publicacion publicacion = loadPublicacionPort.porId(publicacionId)
                .orElseThrow(() -> new NoSuchElementException("Publicacion no encontrada: " + publicacionId));
        if (publicacion.oculta()) {
            throw new NoSuchElementException("Publicacion no encontrada: " + publicacionId);
        }
        return publicacion;
    }

    private Comentario requireComentarioVisible(ComentarioId id) {
        Comentario comentario = loadComentarioPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Comentario no encontrado: " + id));
        if (comentario.oculto()) {
            throw new NoSuchElementException("Comentario no encontrado: " + id);
        }
        return comentario;
    }

    /** Predicado puro — un actor que no existe (X-Actor-Id manipulado, sin JWT real
     * todavia) NO es moderador; no es un error 404. Lanzar aca dejaba distinguir, por el
     * mensaje del 404, "comentario ausente" de "actor ausente" — las dos unicas formas de
     * llegar a esta linea sin haber fallado antes — filtrando si el comentario existia.
     * Fail-closed a "no autorizado" (403) en vez de "no encontrado" (404), ver
     * docs/MODULO_COMMUNITY.md sec. 5 (hallazgo de seguridad). */
    private boolean esModerador(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .map(actor -> actor.status() == UserStatus.ACTIVE
                        && (actor.role() == UserRole.ADMIN || actor.role() == UserRole.ALCHEMIST))
                .orElse(false);
    }

    /** Fail-closed, mismo criterio que {@code esModerador}: actor inexistente o suspendido
     * -> false, nunca una excepcion de tipo distinto — se llama siempre DESPUES de
     * confirmar visibilidad, para que cualquier fallo de actor caiga a 403 y no a un 404
     * con mensaje distinto que delataria que el recurso SI existe (auditoria E2E
     * adversarial: escribir/editar/ocultar un comentario no chequeaban el estado del actor
     * en absoluto). */
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
