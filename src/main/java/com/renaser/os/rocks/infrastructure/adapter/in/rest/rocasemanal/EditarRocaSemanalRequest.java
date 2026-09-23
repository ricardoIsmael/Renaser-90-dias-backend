package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

/**
 * PATCH parcial (W-03): campo ausente/null = no se toca.
 *
 * > <b>Corregido el 2026-09-22.</b> Tenia {@code accionesCriticas}, y el servicio hacia
 * > {@code get(0)}, {@code get(1)}, {@code get(2)} sobre esa lista sin mirar su tamano: mandar dos
 * > acciones era un {@code IndexOutOfBoundsException} — un 500. Se fue junto con la tabla.
 */
public record EditarRocaSemanalRequest(String titulo, String obstaculo,
                                        String contingencia, Integer autoevaluacionInicio) {
}
