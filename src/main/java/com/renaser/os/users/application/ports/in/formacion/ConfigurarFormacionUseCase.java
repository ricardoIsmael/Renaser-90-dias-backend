package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;

import java.util.List;

/** Administración y Alquimista editan nombre, descripción, estado y duraciones de una formación (spec 022, FR-003). Rige solo para quien se acepte después (FR-004). */
public interface ConfigurarFormacionUseCase {

    /**
     * @throws IllegalArgumentException (400) nombre de 3 a 80 letras, descripción de hasta 600, de 1 a 4 duraciones distintas de 1 a 365 días
     * @throws IllegalStateException    (409) si otra persona cambió la formación mientras se editaba (la versión no coincide)
     */
    FormacionConfigurada configurar(ConfigurarFormacionCommand command);

    record ConfigurarFormacionCommand(UserId actor, Formacion formacion, String nombre, String descripcion, boolean activa,
                                      List<Integer> duracionesDias, int version) {
    }
}
