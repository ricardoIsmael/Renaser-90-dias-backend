package com.renaser.os.habits.application.ports.in.carrera;

import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Spec 042 (D-323): guarda una carrera con GPS terminada y, en la misma transacción, aplica sus km al hábito KILÓMETROS DIARIOS del
 * día de la carrera. Idempotente por {@code (persona, carreraId)}: el mismo envío otra vez devuelve lo guardado
 * ({@link CarreraRegistrada#repetida()}) sin tocar el hábito; otro contenido con la misma clave es un
 * {@link CarreraYaRegistradaException} (409).
 */
public interface RegistrarCarreraUseCase {

    CarreraRegistrada registrar(RegistrarCarreraCommand command);

    /** La persona es quien está autenticada: no viaja en el cuerpo. */
    record RegistrarCarreraCommand(UserId actorId, DatosDeCarrera datos) {

        public RegistrarCarreraCommand {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(datos, "datos");
        }
    }

    /** La carrera guardada, con su efecto en el hábito, y si este envío repetía uno ya guardado. */
    record CarreraRegistrada(Carrera carrera, boolean repetida) {
    }

    /** Misma {@code carreraId} con otro contenido: no se pisa la carrera guardada (FR-019). */
    final class CarreraYaRegistradaException extends IllegalStateException {

        public static final String CODIGO = "CARRERA_YA_REGISTRADA";

        public CarreraYaRegistradaException() {
            super("Esta carrera ya está guardada con otros datos. Si es otra carrera, vuelve a grabarla.");
        }
    }
}
