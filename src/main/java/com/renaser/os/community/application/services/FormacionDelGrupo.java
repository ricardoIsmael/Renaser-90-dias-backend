package com.renaser.os.community.application.services;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-010 (spec 022): un grupo es de UNA formación y su mentor y todos sus integrantes son de ella. Nadie se suma a un grupo de otra formación; el
 * mensaje nombra las dos. Administración y Alquimista (que pueden liderar un grupo) pertenecen a todas y no se filtran.
 */
@Component
class FormacionDelGrupo {

    private final AlcanceDeFormacionFinder pertenencias;
    private final FormacionesFinder catalogo;

    FormacionDelGrupo(AlcanceDeFormacionFinder pertenencias, FormacionesFinder catalogo) {
        this.pertenencias = pertenencias;
        this.catalogo = catalogo;
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
