package com.renaser.os.habits.application.ports.in.carrera;

import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.RutaEnviada;
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

    /**
     * La persona es quien está autenticada: no viaja en el cuerpo. {@code rutaEnviada} es la ruta COMPLETA que mandó el teléfono
     * (o {@code null} si no la guarda); el servicio le quita la zona de privacidad antes de guardar (FR-043), así que {@code datos}
     * llega sin ruta cuando hay una ruta enviada.
     */
    record RegistrarCarreraCommand(UserId actorId, DatosDeCarrera datos, RutaEnviada rutaEnviada) {

        public RegistrarCarreraCommand {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(datos, "datos");
            if (rutaEnviada != null && datos.ruta() != null) {
                throw new IllegalArgumentException("La ruta llega una sola vez: la enviada, sin recortar");
            }
        }

        public RegistrarCarreraCommand(UserId actorId, DatosDeCarrera datos) {
            this(actorId, datos, null);
        }

        /** Lo que se guarda: la carrera con la ruta ya sin la zona de privacidad (o sin ruta, si no queda nada). */
        public DatosDeCarrera datosAGuardar() {
            return rutaEnviada == null ? datos : datos.conRuta(rutaEnviada.sinLaZonaDePrivacidad().orElse(null));
        }

        /** La huella de lo que mandó el teléfono, con su ruta completa (FR-019). */
        public String huellaDelEnvio() {
            return datos.huellaDelEnvio(rutaEnviada);
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
