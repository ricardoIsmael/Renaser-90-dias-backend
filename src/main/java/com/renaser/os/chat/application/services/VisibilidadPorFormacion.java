package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.ComunidadDeLaFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Quién ve cada conversación (spec 024, D-301): la UNICA regla del aislamiento de la Tribu, en un solo lugar para que la lista, el acceso de los
 * mensajes, la presencia, la autorización en vivo, el directorio y «Compartir» no la copien (la misma lección que las cuatro copias de la regla de
 * pertenencia de {@code AccesoAChatsDeGrupo}).
 *
 * <p>Una conversación se ve solo si se cumplen LAS TRES:
 * <ol>
 *   <li><b>Es de una formación que la persona ve</b> (FR-025): el alcance de la 022. Administración, Alquimista y Soporte TI ven las tres; el
 *       personal con varias, solo la activa; el aprendiz, la suya. Quien cambia de formación deja de ver lo de la anterior sin que se borre nada.</li>
 *   <li><b>Su tipo de comunidad está prendido en esa formación</b> (FR-035): general, grupo, soporte o directos. Apagarlo esconde, no borra.</li>
 *   <li><b>Si es un directo, la otra persona también pertenece a esa formación</b> (FR-029): dos personas que dejan de compartir formación dejan
 *       de ver el directo, las dos. Administración y Alquimista no pertenecen a una y se escriben con cualquiera.</li>
 * </ol>
 */
@Component
public class VisibilidadPorFormacion {

    private final AlcanceDeFormacionFinder formaciones;
    private final ComunidadDeLaFormacionFinder comunidades;
    private final ListarUsuariosDeConversacionPort usuariosDeConversacion;
    private final UserSummaryFinder userSummaryFinder;

    public VisibilidadPorFormacion(AlcanceDeFormacionFinder formaciones, ComunidadDeLaFormacionFinder comunidades,
                                   ListarUsuariosDeConversacionPort usuariosDeConversacion, UserSummaryFinder userSummaryFinder) {
        this.formaciones = formaciones;
        this.comunidades = comunidades;
        this.usuariosDeConversacion = usuariosDeConversacion;
        this.userSummaryFinder = userSummaryFinder;
    }

    /** Lo que {@code actor} puede leer; {@code pedida} (solo Administración) lo acota a una formación. */
    public AlcanceDeFormacion alcanceDe(UserId actor, Formacion pedida) {
        return formaciones.de(actor).acotadoA(pedida);
    }

    /** El tipo de comunidad al que pertenece cada tipo de conversación. */
    public static TipoDeComunidad tipoDeComunidadDe(TipoConversacion tipo) {
        return switch (tipo) {
            case GLOBAL -> TipoDeComunidad.GENERAL;
            case CELULA -> TipoDeComunidad.GRUPO;
            case SOPORTE -> TipoDeComunidad.SOPORTE;
            case DIRECTA -> TipoDeComunidad.DIRECTO;
        };
    }

    /** Si {@code formacion} ofrece ese tipo de comunidad (los apagados no se ven ni se pueden crear). */
    public boolean tipoPrendido(Formacion formacion, TipoDeComunidad tipo) {
        return comunidades.prendidosDe(formacion).contains(tipo);
    }

    /** Para una sola conversación (el acceso de un mensaje, la presencia, la autorización en vivo). */
    public boolean puedeVer(Conversacion conversacion, UserId actor) {
        return puedeVer(conversacion, formaciones.de(actor), actor);
    }

    /**
     * @throws NoSuchElementException (404) con el mismo texto que una conversación que no existe: la de otra formación no delata que está en otro lado
     */
    public void exigirQueLaVea(Conversacion conversacion, UserId actor) {
        if (!puedeVer(conversacion, actor)) {
            throw new NoSuchElementException("Conversacion no encontrada: " + conversacion.id());
        }
    }

    /** Las conversaciones de {@code conversaciones} que {@code actor} ve, calculando el alcance y los tipos prendidos UNA vez para toda la lista. */
    public List<Conversacion> visiblesPara(Collection<Conversacion> conversaciones, UserId actor, AlcanceDeFormacion alcance) {
        if (conversaciones.isEmpty()) {
            return List.of();
        }
        Map<Formacion, Set<TipoDeComunidad>> prendidos = prendidosPorFormacion(conversaciones);
        Set<ConversacionId> directosSinCompanero = directosSinCompaneroDeLaFormacion(conversaciones, actor);
        List<Conversacion> visibles = new ArrayList<>(conversaciones.size());
        for (Conversacion c : conversaciones) {
            if (alcance.incluye(c.formacion())
                    && prendidos.get(c.formacion()).contains(tipoDeComunidadDe(c.tipo()))
                    && !directosSinCompanero.contains(c.id())) {
                visibles.add(c);
            }
        }
        return visibles;
    }

    private boolean puedeVer(Conversacion conversacion, AlcanceDeFormacion alcance, UserId actor) {
        if (!alcance.incluye(conversacion.formacion()) || !tipoPrendido(conversacion.formacion(),
                tipoDeComunidadDe(conversacion.tipo()))) {
            return false;
        }
        return conversacion.tipo() != TipoConversacion.DIRECTA
                || directosSinCompaneroDeLaFormacion(List.of(conversacion), actor).isEmpty();
    }

    private Map<Formacion, Set<TipoDeComunidad>> prendidosPorFormacion(Collection<Conversacion> conversaciones) {
        Map<Formacion, Set<TipoDeComunidad>> prendidos = new EnumMap<>(Formacion.class);
        conversaciones.stream().map(Conversacion::formacion).distinct()
                .forEach(f -> prendidos.put(f, comunidades.prendidosDe(f)));
        return prendidos;
    }

    /**
     * De los directos de la lista, los que ya no tienen a la otra persona dentro de la formación del directo (FR-029): una consulta por formación
     * para los participantes y una por los roles, nunca una por directo. El personal que ve todas las formaciones cuenta como de cualquiera.
     */
    private Set<ConversacionId> directosSinCompaneroDeLaFormacion(Collection<Conversacion> conversaciones, UserId actor) {
        List<Conversacion> directos = conversaciones.stream().filter(c -> c.tipo() == TipoConversacion.DIRECTA).toList();
        if (directos.isEmpty()) {
            return Set.of();
        }
        Map<ConversacionId, UserId> otros = usuariosDeConversacion.otroParticipanteDeDirectas(
                directos.stream().map(Conversacion::id).toList(), actor);
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(otros.values().stream().distinct().toList());
        Set<ConversacionId> sinCompanero = new HashSet<>();
        for (Formacion formacion : directos.stream().map(Conversacion::formacion).distinct().toList()) {
            List<Conversacion> deEsaFormacion = directos.stream().filter(c -> c.formacion() == formacion).toList();
            Set<UserId> candidatos = new HashSet<>();
            deEsaFormacion.forEach(c -> Optional.ofNullable(otros.get(c.id())).ifPresent(candidatos::add));
            Set<UserId> pertenecen = formaciones.dentroDelAlcance(candidatos, AlcanceDeFormacion.de(formacion));
            for (Conversacion directo : deEsaFormacion) {
                UserId otro = otros.get(directo.id());
                if (otro == null || !(pertenecen.contains(otro) || veTodasLasFormaciones(perfiles.get(otro)))) {
                    sinCompanero.add(directo.id());
                }
            }
        }
        return sinCompanero;
    }

    /** Administración, Alquimista y Soporte TI no pertenecen a una formación: ven las tres, así que son del directo de cualquiera. */
    private static boolean veTodasLasFormaciones(UserSummary persona) {
        return persona != null && (persona.role() == UserRole.ADMIN || persona.role() == UserRole.ALCHEMIST
                || persona.role() == UserRole.IT_SUPPORT);
    }
}
