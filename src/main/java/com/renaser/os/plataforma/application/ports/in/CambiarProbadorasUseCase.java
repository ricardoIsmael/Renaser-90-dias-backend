package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Suma o quita a una persona de los probadores de un despliegue (contrato fase 2 §3.4): la ve aunque su cubeta quede
 * fuera del porcentaje, también al 0 %. Queda auditado (con quién en el motivo); no avisa por push.
 */
public interface CambiarProbadorasUseCase {

    /** 404 si la persona no existe; 409 si la funcionalidad no está en despliegue. Sumarla dos veces no hace nada. */
    DespliegueEnConsola sumar(ProbadoraCommand command);

    /** Quitar a quien no era probadora (o sin despliegue) no hace nada. */
    void quitar(ProbadoraCommand command);

    /** @param motivo opcional; la auditoría anota además a quién se sumó o quitó */
    record ProbadoraCommand(AccesoDeConsola acceso, String clave, UserId persona, String motivo, String dispositivo) {

        public ProbadoraCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
            Objects.requireNonNull(persona, "persona es obligatoria");
        }
    }
}
