package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GuardarRespuestaRequest(@NotNull Integer questionId, String textValue, BigDecimal numberValue,
                                       Boolean booleanValue, Short scaleValue, String jsonValue, Long mediaId) {
}
