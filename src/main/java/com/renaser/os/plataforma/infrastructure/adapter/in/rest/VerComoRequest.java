package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import java.util.UUID;

/**
 * {@code POST /soporte-ti/ver-como} (contrato fase-2.md §5): {@code { "rol": "MENTOR" }} o
 * {@code { "rolPersonalizadoId": "…" }}, uno de los dos. El rol va con el nombre de la API ({@code TRAINEE},
 * {@code TRAINEE_GUIDE}, {@code MENTOR}, {@code MENTOR_LEAD}, {@code ADMIN}, {@code ALCHEMIST}).
 */
record VerComoRequest(String rol, UUID rolPersonalizadoId) {
}
