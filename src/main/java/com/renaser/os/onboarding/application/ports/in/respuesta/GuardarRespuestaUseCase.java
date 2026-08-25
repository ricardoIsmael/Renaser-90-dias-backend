package com.renaser.os.onboarding.application.ports.in.respuesta;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Upsert (unico usuario+pregunta): guardar de nuevo sobre la misma pregunta actualiza, no
 * duplica (ver {@code Respuesta.actualizarValor} / {@code SaveRespuestaPort}).
 *
 * <p>Los 5 campos de valor son todos opcionales a nivel de comando (Bean Validation no
 * puede saber cual corresponde sin conocer el tipo de la pregunta) — la coherencia con
 * {@code tipo_pregunta_onboarding} la valida el DOMINIO ({@code Respuesta.crear}), nivel 3
 * de validacion (CLAUDE.MD §5.4.3), despues de que el servicio cargue la pregunta.
 */
public interface GuardarRespuestaUseCase {

    Respuesta guardar(GuardarRespuestaCommand command);

    record GuardarRespuestaCommand(@NotNull UserId usuarioId, @NotNull Integer preguntaId, String valorTexto,
                                    BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                                    String valorJson, Long mediaId) {

        public GuardarRespuestaCommand {
            SelfValidating.validateConstructorArgs(GuardarRespuestaCommand.class, usuarioId, preguntaId, valorTexto,
                    valorNumero, valorBooleano, valorEscala, valorJson, mediaId);
        }
    }
}
