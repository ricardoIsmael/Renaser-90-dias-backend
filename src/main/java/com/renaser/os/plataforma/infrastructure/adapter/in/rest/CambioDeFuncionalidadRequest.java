package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * {@code PUT /soporte-ti/funcionalidades/{clave}} (contrato §6): el estado completo que se quiere, con motivo
 * obligatorio y la versión que se estaba mirando.
 */
record CambioDeFuncionalidadRequest(@NotNull Boolean prendidaGlobal, Map<String, Boolean> porRol,
                                    @Size(max = 200) String aviso, @NotBlank @Size(max = 500) String motivo,
                                    @NotNull Long version) {
}
