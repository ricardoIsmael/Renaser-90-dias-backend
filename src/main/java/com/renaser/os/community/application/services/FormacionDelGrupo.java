package com.renaser.os.community.application.services;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-010 (spec 022): un grupo es de UNA formación y su mentor y todos sus integrantes son de ella. Nadie se suma a un grupo de otra formación; el
 * mensaje nombra las dos. Administración y Alquimista (que pueden liderar un grupo) pertenecen a todas y no se filtran.
 *
 * <p>Spec 056 (FR-010, decisión del dueño del 18/11): quien arma grupos ya no es solo Administración y Alquimista; el líder de mentores también, pero
 * <b>solo en las formaciones que tiene activas</b> ({@link #exigirQueGestioneGrupos}, {@link #exigirQueLaGestione}, {@link #gestionablesPor},
 * {@link #ofrecibles}). Fuera de ellas es un 403 {@code FUERA_DE_SU_ALCANCE}, no un 404: el líder sabe que el grupo existe, solo que no le toca.
 */
@Component
class FormacionDelGrupo {

    private final AlcanceDeFormacionFinder pertenencias;
    private final FormacionesFinder catalogo;

    FormacionDelGrupo(AlcanceDeFormacionFinder pertenencias, FormacionesFinder catalogo) {
        this.pertenencias = pertenencias;
        this.catalogo = catalogo;
    }

    /**
     * Abrir un grupo por su id: es un 404 si es de una formación que quien actúa no ve (spec 022, FR-021). Un mentor con dos formaciones solo abre los grupos de la
     * que tiene activa, igual que en sus listas.
     *
     * @throws com.renaser.os.shared.domain.ObjetoDeOtraFormacionException (404)
     */
    void exigirQueLoVea(UserId actor, Celula grupo) {
        pertenencias.exigirQueLaVea(actor, grupo.formacion());
    }

    /**
     * Quién arma grupos (spec 056, FR-010): Administración, Alquimista y el líder de mentores, con la cuenta ya comprobada como activa por quien llama.
     * Cualquier otro rol es 403.
     */
    void exigirQueGestioneGrupos(UserSummary actor) {
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST && actor.role() != UserRole.MENTOR_LEAD) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST y el líder de mentores administran celulas");
        }
    }

    /** Si el alcance de {@code actor} se acota a sus formaciones: solo el líder de mentores; Administración y Alquimista ven todas. */
    boolean acotaAlAlcance(UserSummary actor) {
        return actor.role() == UserRole.MENTOR_LEAD;
    }

    /**
     * El líder solo gestiona los grupos de las formaciones que tiene activas (spec 056, FR-010); para Administración y Alquimista no cambia nada.
     *
     * @throws NotAuthorizedException (403, {@code FUERA_DE_SU_ALCANCE}) si el grupo es de otra formación
     */
    void exigirQueLaGestione(UserSummary actor, Formacion formacionDelGrupo) {
        if (acotaAlAlcance(actor) && !pertenencias.de(actor.id()).incluye(formacionDelGrupo)) {
            throw new NotAuthorizedException("Ese grupo es de otra formación: tú gestionas solo los de tus formaciones.",
                    NotAuthorizedException.FUERA_DE_SU_ALCANCE);
        }
    }

    /** De estos grupos, los que {@code actor} puede gestionar: los de las formaciones del líder; todos para Administración y Alquimista. */
    List<Celula> gestionablesPor(UserSummary actor, List<Celula> grupos) {
        return acotaAlAlcance(actor) ? visiblesPara(actor.id(), grupos) : grupos;
    }

    /**
     * De estos candidatos, los que {@code actor} puede ofrecer en el selector de un grupo: los de la formación pedida ({@code null} = todas) y, si es el
     * líder, solo los de sus formaciones (una consulta). Así el líder nunca ve a una persona de otra formación aunque no pida filtro.
     */
    List<UserId> ofrecibles(UserSummary actor, Formacion pedida, List<UserId> candidatos) {
        if (!acotaAlAlcance(actor)) {
            return pedida == null ? candidatos : soloDe(pedida, candidatos);
        }
        Set<UserId> dentro = pertenencias.dentroDelAlcance(candidatos, pertenencias.de(actor.id()).acotadoA(pedida));
        return candidatos.stream().filter(dentro::contains).toList();
    }

    /** De estos grupos, los de las formaciones que {@code actor} ve: la lista de un mentor con dos formaciones es la de la que tiene activa. */
    List<Celula> visiblesPara(UserId actor, List<Celula> grupos) {
        AlcanceDeFormacion alcance = pertenencias.de(actor);
        return grupos.stream().filter(grupo -> alcance.incluye(grupo.formacion())).toList();
    }

    /** @throws AsignacionInvalidaException (409) si el aprendiz no es de la formación del grupo */
    void exigirQueElAprendizSea(UserId aprendiz, Celula grupo) {
        exigir(aprendiz, grupo);
    }

    /** @throws AsignacionInvalidaException (409) si el mentor no trabaja en la formación del grupo (salvo Administración y Alquimista) */
    void exigirQueElMentorSea(UserId mentor, UserRole rol, Celula grupo) {
        if (rol == UserRole.ADMIN || rol == UserRole.ALCHEMIST) {
            return;
        }
        exigir(mentor, grupo);
    }

    /** De estos candidatos, los que pertenecen a {@code formacion} (una consulta): el selector de un grupo no ofrece a gente de otra formación. */
    List<UserId> soloDe(Formacion formacion, List<UserId> candidatos) {
        Set<UserId> dentro = pertenencias.dentroDelAlcance(candidatos, AlcanceDeFormacion.de(formacion));
        return candidatos.stream().filter(dentro::contains).toList();
    }

    private void exigir(UserId persona, Celula grupo) {
        Set<Formacion> delaPersona = pertenencias.formacionesDe(persona);
        if (delaPersona.contains(grupo.formacion())) {
            return;
        }
        String suyas = delaPersona.isEmpty() ? "ninguna formación" : "la formación " + nombres(delaPersona);
        throw new AsignacionInvalidaException("No se puede sumar a esta persona al grupo «" + grupo.nombre() + "»: es de " + suyas
                + " y el grupo es de la formación " + catalogo.de(grupo.formacion()).nombre() + ".");
    }

    private String nombres(Set<Formacion> formaciones) {
        return formaciones.stream().sorted().map(f -> catalogo.de(f).nombre()).collect(Collectors.joining(" y "));
    }
}
