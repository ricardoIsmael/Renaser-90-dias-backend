package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /soporte-ti/funcionalidades/{clave}/despliegue} (contrato fase 2 §3.4): el escalón que se quiere (100 =
 * «prender para todos»), con motivo obligatorio y, opcional, la versión que se estaba mirando.
 */
record CambioDeDespliegueRequest(@NotNull @Min(0) @Max(100) Integer porcentaje,
                                 @NotBlank @Size(max = 500) String motivo, Long version) {
}
