package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

/** {@code image/jpeg} o {@code image/png}: el mismo que el teléfono va a mandar en el PUT (S3 lo firma). */
record SolicitarSubidaDePortadaRequest(String contentType) {
}
