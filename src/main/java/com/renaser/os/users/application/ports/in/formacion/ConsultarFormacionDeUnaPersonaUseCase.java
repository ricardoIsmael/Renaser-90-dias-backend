package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.formacion.ParticipacionAnterior;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;

import java.util.List;

/** Lo que Administración ve de las formaciones de una persona en su ficha (spec 022, FR-009, FR-029): la de su programa, las del personal y lo que cursó antes. */
public interface ConsultarFormacionDeUnaPersonaUseCase {

    /**
     * @throws java.util.NoSuchElementException (404) si la persona no existe
     * @throws com.renaser.os.shared.domain.NotAuthorizedException (403) si quien pide no es Administración o Alquimista
     */
    FormacionDeUnaPersona consultar(UserId actor, UserId persona);

    /**
     * @param formacion        la formación de su programa (null si no lo hace)
     * @param duracionDias     la duración de su programa (90 si no lo hace)
     * @param programaActivado si ya eligió su Día 1: sin él, corregir la formación no deja historia (FR-030)
     * @param delPersonal      las formaciones asignadas, solo del personal
     * @param historial        lo que cursó antes, lo más reciente primero
     */
    record FormacionDeUnaPersona(Formacion formacion, int duracionDias, boolean programaActivado, List<Asignacion> delPersonal,
                                 List<ParticipacionAnterior> historial) {
    }
}
