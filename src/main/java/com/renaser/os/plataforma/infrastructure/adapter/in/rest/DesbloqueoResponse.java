package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import java.time.Instant;

/** La respuesta del desbloqueo: el token para {@code X-Consola-Token} o {@code X-Reautenticacion}. */
record DesbloqueoResponse(String token, Instant venceEn) {
}
