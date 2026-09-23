package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CrearPlanSemanalRequest(@NotEmpty List<@Valid ItemRocaSemanalRequest> rocas) {

    /**
     * > <b>Corregido el 2026-09-22.</b> Tenia {@code accionCritica1/2/3}. Las acciones pasaron al
     * > objetivo diario (V61) y la app dejo de mandarlas. Una version vieja que todavia las mande
     * > no rompe: Jackson ignora los campos que sobran, asi que llegan y se descartan.
     */
    public record ItemRocaSemanalRequest(@NotBlank String eje, @NotBlank String titulo, String obstaculo,
                                          String contingencia, Integer autoevaluacionInicio) {
    }
}
