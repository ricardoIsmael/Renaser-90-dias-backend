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

    @Test
    @DisplayName("D-212: una foto de más de 2 MB sale 413 con un motivo legible; sin la parte del multipart, 400")
    void elMultipartDeLaFotoDelGrupo() {
        var pesada = handler.handleArchivoDemasiadoGrande(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(2 * 1024 * 1024));
        var sinParte = handler.handleMissingPart(
                new org.springframework.web.multipart.support.MissingServletRequestPartException("foto"));

        assertThat(pesada.getStatusCode().value()).isEqualTo(413);
        assertThat(pesada.getBody()).isNotNull();
        assertThat(pesada.getBody().message()).contains("2 MB");
        assertThat(sinParte.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sinParte.getBody().message()).contains("foto");
    }

    @Test
    @DisplayName("spec 002: funcionalidad apagada sale 503 con el aviso como message, el código y la clave; sin aviso, el "
            + "genérico; consola bloqueada 423 y reautenticación 428, con su código")
    void losErroresDeLaPlataforma() {
        var apagada = handler.handleFuncionalidadApagada(new com.renaser.os.shared.domain.FuncionalidadApagadaException(
                "chat.grupos", "Volvemos a las 18:00"));
        var sinAviso = handler.handleFuncionalidadApagada(new com.renaser.os.shared.domain.FuncionalidadApagadaException(
                "muro", "  "));
        var bloqueada = handler.handleConsolaBloqueada(new com.renaser.os.shared.domain.ConsolaBloqueadaException());
        var reautenticar = handler.handleReautenticacionRequerida(
                new com.renaser.os.shared.domain.ReautenticacionRequeridaException());

        assertThat(apagada.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(apagada.getBody()).isNotNull();
        assertThat(apagada.getBody().message()).isEqualTo("Volvemos a las 18:00");
        assertThat(apagada.getBody().codigo()).isEqualTo("FUNCIONALIDAD_APAGADA");
        assertThat(apagada.getBody().funcionalidad()).isEqualTo("chat.grupos");
        assertThat(sinAviso.getBody().message())
                .isEqualTo("Esta función está en mantenimiento. Vuelve a intentarlo en un rato.");
        assertThat(bloqueada.getStatusCode().value()).isEqualTo(423);
        assertThat(bloqueada.getBody().codigo()).isEqualTo("CONSOLA_BLOQUEADA");
        assertThat(bloqueada.getBody().funcionalidad()).isNull();
        assertThat(reautenticar.getStatusCode().value()).isEqualTo(428);

        var noDisponible = handler.handleFuncionalidadNoDisponible(
                new com.renaser.os.shared.domain.FuncionalidadNoDisponibleException("eventos"));
        assertThat(noDisponible.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noDisponible.getBody().codigo()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
        assertThat(noDisponible.getBody().funcionalidad()).isEqualTo("eventos");
        assertThat(noDisponible.getBody().message()).isEqualTo("Esta función todavía no está disponible para tu cuenta.");
        assertThat(reautenticar.getBody().codigo()).isEqualTo("REAUTENTICACION_REQUERIDA");
    }
}
