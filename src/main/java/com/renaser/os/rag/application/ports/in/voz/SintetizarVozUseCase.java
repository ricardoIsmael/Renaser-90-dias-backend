package com.renaser.os.rag.application.ports.in.voz;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Lo que pide el orbe de voz de la app: "decime esto en voz alta". Devuelve el audio WAV, o vacio
 * cuando no hay voz del servidor — la app entonces usa el TTS del telefono.
 *
 * <p>Solo una cuenta activa puede pedirlo (una suspendida recibe {@code NotAuthorizedException}),
 * y el texto va recortado, no vacio y de a lo sumo {@link #LARGO_MAXIMO_TEXTO} caracteres
 * ({@code IllegalArgumentException} si no). El tope existe porque cada llamada ocupa CPU del
 * servicio de voz: la app manda de a una oracion o un parrafo corto, nunca una respuesta entera.
 */
public interface SintetizarVozUseCase {

    int LARGO_MAXIMO_TEXTO = 400;

    Optional<byte[]> sintetizar(UserId actorId, String texto);
}
