package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CrearPlanSemanalRequest(@NotEmpty List<@Valid ItemRocaSemanalRequest> rocas) {

    public record ItemRocaSemanalRequest(@NotBlank String eje, @NotBlank String titulo, String accionCritica1, String accionCritica2,
                                          String accionCritica3, String obstaculo, String contingencia,
                                          Integer autoevaluacionInicio) {
    }
}
