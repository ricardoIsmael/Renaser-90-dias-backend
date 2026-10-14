package com.renaser.os.plataforma.application.ports.in;

import java.util.Map;
import java.util.Objects;

/**
 * Cambia el interruptor de una funcionalidad (contrato §6, {@code PUT /soporte-ti/funcionalidades/{clave}}): para todos
 * y por rol, con aviso y motivo obligatorio. Queda auditado y avisa por push (FR-007, FR-008).
 */
public interface CambiarFuncionalidadUseCase {

    FuncionalidadEnConsola cambiar(CambiarFuncionalidadCommand command);

    /**
     * @param porRol           {@code {"TRAINEE": false}}: un rol en {@code false} queda apagado; uno en {@code true} o
     *                         ausente, prendido. Las claves son los nombres de {@code UserRole}
     * @param reautenticacion  el token de {@code X-Reautenticacion}; obligatorio si el cambio es crítico (428)
     */
    record CambiarFuncionalidadCommand(AccesoDeConsola acceso, String clave, Boolean prendidaGlobal,
                                       Map<String, Boolean> porRol, String aviso, String motivo, Long version,
                                       String reautenticacion, String dispositivo) {

        public CambiarFuncionalidadCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
            porRol = porRol == null ? Map.of() : Map.copyOf(porRol);
        }
    }
}
