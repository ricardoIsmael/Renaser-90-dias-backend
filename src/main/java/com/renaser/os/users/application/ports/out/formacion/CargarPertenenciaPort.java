package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A qué formación pertenece una persona, tal como lo dice la base (spec 022): su rol, la formación y la duración de su participación en el programa
 * (si la tiene) y las formaciones que se le asignaron (si es personal).
 */
public interface CargarPertenenciaPort {

    Optional<Pertenencia> de(UserId usuario);

    /** De {@code personas}, las que pertenecen a alguna formación del alcance (una consulta). */
    Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance);

    /**
     * @param deLaParticipacion {@code null} si no tiene fila en {@code participantes_programa}
     * @param asignadas         solo para el personal; vacía para el resto
     */
    record Pertenencia(UserRole rol, Formacion deLaParticipacion, int duracionDias, boolean programaActivado,
                       List<Asignacion> asignadas) {

        public Pertenencia {
            asignadas = List.copyOf(asignadas);
        }
    }
}
