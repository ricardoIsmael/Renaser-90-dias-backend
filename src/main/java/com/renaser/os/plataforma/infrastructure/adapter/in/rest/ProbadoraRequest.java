package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Size;

/** {@code PUT …/despliegue/probadores/{usuarioId}} (contrato fase 2 §3.4): el cuerpo es opcional, y el motivo también. */
record ProbadoraRequest(@Size(max = 500) String motivo) {
}
