package com.renaser.os.rocks.application.ports.in.cierre;

import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cerrar el día de hoy (spec 017). Idempotente: vuelve a escribir todas las respuestas del día. <b>Nunca completa una acción, paga
 * puntos ni publica eventos</b> (FR-003).
 */
public interface CerrarElDiaUseCase {

    /**
     * @throws com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException (409) si {@code fecha} ya pasó
     * @throws IllegalArgumentException (400) fecha futura, falta la respuesta de una pendiente, una acción ajena o repetida, un
     *                                  motivo o plan desconocido, una nota de más de 500 caracteres
     * @throws com.renaser.os.shared.domain.NotAuthorizedException cuenta suspendida, o personal sin su programa activado
     */
    VistaDelCierre cerrar(CerrarElDia command);

    /**
     * @param fecha    el día que la persona estaba viendo: se compara con «hoy» en el servidor, no se confía en él
     * @param acciones una por cada acción pendiente; las de acciones ya cumplidas se ignoran
     * @param nota     la nota del día (opcional)
     */
    record CerrarElDia(UserId actorId, LocalDate fecha, List<RespuestaDeAccion> acciones, String nota) {

        public CerrarElDia {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(fecha, "fecha es obligatoria");
            acciones = acciones == null ? List.of() : List.copyOf(acciones);
        }
    }

    /** Lo que llegó, sin interpretar: {@code motivo} y {@code manana} son texto; el servicio los lleva al dominio. */
    record RespuestaDeAccion(UUID accionId, String motivo, String manana, String nota) {
    }
}
