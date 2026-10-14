package com.renaser.os.plataforma.application.ports.in;

import java.util.Objects;

/**
 * Mueve el escalón de un despliegue (contrato fase 2 §3.4, {@code PUT /soporte-ti/funcionalidades/{clave}/despliegue}):
 * empezarlo (menos de 100 % sin despliegue), subirlo, bajarlo o terminarlo (100 %, «prender para todos»). Queda
 * auditado y avisa por push como un interruptor (FR-019).
 */
public interface CambiarDespliegueUseCase {

    DespliegueEnConsola cambiar(CambiarDespliegueCommand command);

    /**
     * @param porcentaje      0 a 100; {@code null} es un pedido inválido (400)
     * @param version         la que estaba mirando, opcional: si viene y no es la vigente, 409
     * @param reautenticacion el token de {@code X-Reautenticacion}; obligatorio si el paso es crítico (428)
     */
    record CambiarDespliegueCommand(AccesoDeConsola acceso, String clave, Integer porcentaje, String motivo,
                                    Long version, String reautenticacion, String dispositivo) {

        public CambiarDespliegueCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }
}
