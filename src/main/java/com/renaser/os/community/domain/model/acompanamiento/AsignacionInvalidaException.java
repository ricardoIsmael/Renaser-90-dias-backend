package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Una invariante temporal rechazada por el dominio. Se lanza antes de tocar la base para que
 * el fallo llegue como regla de negocio; la base repite la restricción con índices únicos
 * parciales porque un check-then-insert no gana una carrera (plan.md §3).
 *
 * <blockquote><b>Extiende {@link IllegalStateException} para salir como 409, no como 500.</b>
 * Antes heredaba de {@code RuntimeException} pelada y caía en el handler genérico:
 * "el grupo Fénix no tiene cupo disponible" —una regla de negocio perfectamente explicable—
 * llegaba al cliente como <i>500 Internal Server Error</i>. El panel no podía distinguir "no
 * hay cupo" de "el backend se rompió", así que le mostraba al administrador un error genérico
 * en vez del motivo real.
 *
 * <p>Es 409 y no 400 porque el conflicto es con el ESTADO ACTUAL —el grupo está lleno, esa
 * persona ya pertenece a otro, ese aprendiz no está en el grupo indicado—, no con la forma de
 * lo que se mandó. Mismo criterio que el resto de los conflictos de este código
 * ("ese mentor ya lidera otra celula").
 *
 * <p>El mapeo se hace por herencia y no agregando un {@code @ExceptionHandler} propio a
 * propósito: {@code shared.web} no debe importar el dominio de {@code community}.
 * Lo fija {@code GlobalExceptionHandlerTest} para que no se pierda en silencio.</blockquote>
 */
public class AsignacionInvalidaException extends IllegalStateException {

    public AsignacionInvalidaException(String message) {
        super(message);
    }
}
