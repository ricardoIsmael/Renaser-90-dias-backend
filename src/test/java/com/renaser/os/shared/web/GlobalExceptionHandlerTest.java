package com.renaser.os.shared.web;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que cada fallo salga con el codigo que le corresponde.
 *
 * <p><b>Por que existe esta clase.</b> El mapeo de {@link AsignacionInvalidaException} se hace por
 * HERENCIA —extiende {@link IllegalStateException}, que el handler ya manda a 409— y no con un
 * {@code @ExceptionHandler} propio, porque {@code shared.web} no debe importar el dominio de
 * {@code community}. Es la solucion correcta y tambien la mas facil de romper sin querer: a nadie
 * le llama la atencion cambiar de que hereda una excepcion, y el sintoma aparece lejos, en la
 * pantalla de un administrador que ve "error del servidor" donde deberia leer "el grupo esta
 * lleno".
 *
 * <p>Lo encontro una prueba E2E: llenar un grupo de diez e intentar el once devolvia <b>500</b>.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Una invariante de asignacion rechazada sale 409, nunca 500")
    void asignacionInvalidaEsConflicto() {
        var respuesta = handler.handleConflict(
                new AsignacionInvalidaException("El grupo Fenix no tiene cupo disponible"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Y el motivo viaja: sin el, el cliente sabe que fallo pero no que decirle a la persona.
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().message()).contains("cupo");
    }

    @Test
    @DisplayName("La herencia que sostiene ese mapeo esta puesta")
    void laHerenciaEsLaQueMapea() {
        /* Se afirma la relacion, no solo el resultado: si alguien vuelve a hacerla extender
           RuntimeException, el metodo de arriba ni siquiera compilaria... pero podria compilar si
           ademas cambiara la firma. Esta asercion falla igual, y dice exactamente por que. */
        assertThat(new AsignacionInvalidaException("x"))
                .as("AsignacionInvalidaException tiene que seguir siendo un IllegalStateException: "
                        + "de ahi sale su 409")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Sin permiso sale 403 y no encontrado sale 404")
    void losOtrosDosCodigosQueMasSeUsan() {
        assertThat(handler.handleNotAuthorized(new NotAuthorizedException("no")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleNotFound(new NoSuchElementException("nada")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
