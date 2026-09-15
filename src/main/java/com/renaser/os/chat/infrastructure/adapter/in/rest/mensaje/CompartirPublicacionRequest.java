package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Compartir una publicacion del Muro en esta conversacion. <b>Solo el id</b>: el texto, el nombre
 * del autor y la foto los resuelve el servidor (ver {@code CompartirPublicacionUseCase}).
 *
 * <p>{@link UUID} y no {@code String} como en {@code CrearConversacionDirectaRequest}: aca no hay
 * nada que traducir a mano, asi que un id mal formado lo rechaza Jackson con 400 antes de entrar
 * al controller, en vez de llegar a un {@code UUID.fromString} adentro.
 */
public record CompartirPublicacionRequest(@NotNull UUID postId) {
}
