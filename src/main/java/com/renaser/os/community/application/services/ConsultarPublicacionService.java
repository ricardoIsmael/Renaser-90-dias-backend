package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Lee UNA publicación del Muro para quien revisa a su autor (spec 005, R-6). A diferencia del feed, que todos los
 * activos ven, esta puerta es la de las fichas de seguimiento y solo abre para:
 * <ul>
 *   <li>su autor;</li>
 *   <li>el líder de mentores, Administración y Alquimista;</li>
 *   <li>un mentor o guía de aprendiz que acompaña HOY a un grupo en el que el autor es aprendiz (el mismo criterio de
 *       la ficha del mentor: relación vigente, no «alguna vez»).</li>
 * </ul>
 * Todo lo demás, 403. Una publicación oculta o borrada (borrar oculta, moderar también), inexistente o de una cuenta
 * cerrada esperando su borrado (D-243) es 404 para todos: la ficha dice «Ya no está disponible».
 *
 * <p>Va aparte de {@code PublicacionMuroService}, que ya tiene mucho: esto es una lectura con su propia regla de acceso.
 */
@Service
public class ConsultarPublicacionService implements ConsultarPublicacionUseCase {

    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);
    private static final Set<UserRole> LIDERAZGO = Set.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private final LoadPublicacionPort publicaciones;
    private final ConsultarPerfilUsuarioPort perfiles;
    private final AlmacenamientoPort almacenamiento;
    private final UserSummaryFinder usuarios;
    private final LoadAsignacionesPort asignaciones;
    private final AcompanamientoFinder acompanamiento;
    private final CuentasCerradasFinder cuentasCerradas;
    private final Clock clock;

    ConsultarPublicacionService(LoadPublicacionPort publicaciones, ConsultarPerfilUsuarioPort perfiles,
                                AlmacenamientoPort almacenamiento, UserSummaryFinder usuarios,
                                LoadAsignacionesPort asignaciones, AcompanamientoFinder acompanamiento,
                                CuentasCerradasFinder cuentasCerradas, Clock clock) {
        this.publicaciones = publicaciones;
        this.perfiles = perfiles;
        this.almacenamiento = almacenamiento;
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.acompanamiento = acompanamiento;
        this.cuentasCerradas = cuentasCerradas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicacionParaLeer publicacionDe(UserId actorId, PublicacionId publicacionId) {
        UserSummary actor = requireActorActivo(actorId);
        Publicacion publicacion = publicaciones.porId(publicacionId).filter(p -> !p.oculta())
                .orElseThrow(() -> new NoSuchElementException("Publicacion no encontrada: " + publicacionId));
        requirePuedeLeer(actor, publicacion.autorId());
        if (!cuentasCerradas.cerradasEntre(List.of(publicacion.autorId())).isEmpty()) {
            throw new NoSuchElementException("Publicacion no encontrada: " + publicacionId);
        }
        return armar(publicacion);
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }

    private void requirePuedeLeer(UserSummary actor, UserId autorId) {
        boolean puede = actor.id().equals(autorId) || LIDERAZGO.contains(actor.role())
                || (actor.role().actuaComoMentor() && acompanaHoyAlAutor(actor.id(), autorId));
        if (!puede) {
            throw new NotAuthorizedException("Esta publicacion solo la lee su autor, el liderazgo o quien lo acompana");
        }
    }

    /** Hoy, no «alguna vez»: el autor es aprendiz vigente de un grupo que el actor acompaña vigentemente. */
    private boolean acompanaHoyAlAutor(UserId mentorId, UserId autorId) {
        Instant ahora = clock.now();
        return asignaciones.porUsuario(autorId).stream()
                .filter(asignacion -> asignacion.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(asignacion -> asignacion.vigenteEn(ahora))
                .anyMatch(asignacion -> acompanamiento.acompanaVigente(mentorId, asignacion.celulaId().value(), ahora));
    }

    private PublicacionParaLeer armar(Publicacion publicacion) {
        PerfilUsuario autor = perfiles.porId(publicacion.autorId()).orElse(null);
        List<MediaFirmada> media = publicacion.media().stream()
                .sorted(Comparator.comparingInt(m -> m.orden()))
                .map(m -> new MediaFirmada(almacenamiento.firmarLectura(m.ruta(), VALIDEZ_URL_LECTURA), m.mime(),
                        m.orden()))
                .toList();
        return new PublicacionParaLeer(publicacion.id().value(), publicacion.autorId(),
                autor == null ? null : autor.nombreCompleto(), autor == null ? null : autor.avatarUrl(),
                publicacion.texto(), publicacion.creadoEn(), media);
    }
}
