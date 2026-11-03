package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;

import java.util.List;
import java.util.Set;

/** Las formaciones en las que trabaja el personal (tabla {@code formaciones_personal}, V126). */
public interface FormacionesDelPersonalPort {

    List<Asignacion> de(UserId persona);

    /** Deja asignadas exactamente {@code formaciones}, con {@code activa} como la elegida. Conserva la fecha de las que ya tenía. */
    void reemplazar(UserId persona, Set<Formacion> formaciones, Formacion activa, UserId asignadaPor);

    /** Marca {@code formacion} como la activa (ya debe estar asignada). */
    void marcarActiva(UserId persona, Formacion formacion);

    /** Asigna {@code formacion} si la persona no tiene ninguna (queda activa); si ya tiene alguna, no toca nada. */
    void asignarSiNoTieneNinguna(UserId persona, Formacion formacion);

    void quitarTodas(UserId persona);
}
