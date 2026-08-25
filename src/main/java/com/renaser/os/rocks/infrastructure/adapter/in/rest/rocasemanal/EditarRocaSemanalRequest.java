package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import java.util.List;

/** PATCH parcial (W-03): campo ausente/null = no se toca. */
public record EditarRocaSemanalRequest(String titulo, List<String> accionesCriticas, String obstaculo,
                                        String contingencia, Integer autoevaluacionInicio) {
}
