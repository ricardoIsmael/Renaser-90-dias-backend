package com.renaser.os.chat.application.ports.in.caja;

import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.time.Instant;

/**
 * La carta con el nombre de la Caja Renaser (D-219): el Admin la descarga para imprimirla y puede cambiarle
 * el fondo. Vive en {@code chat} porque es la misma maquinaria que la tarjeta de bienvenida (Java2D, Cinzel y
 * la bitácora {@code cambios_bienvenida}). Solo ADMIN activo.
 */
public interface CartaDeCajaUseCase {

    /** @throws java.util.NoSuchElementException si no es un aprendiz (404) */
    byte[] carta(UserId actorId, UserId aprendizId);

    FondoDeLaCarta fondo(UserId actorId);

    UrlDeSubida solicitarSubidaDeFondo(UserId actorId, String tipoContenido);

    FondoDeLaCarta confirmarFondo(UserId actorId, String ruta);

    FondoDeLaCarta volverAlFondoOriginal(UserId actorId);

    /**
     * @param cambiado       si hoy sale un fondo que subió el Admin (y no el original)
     * @param sePuedeCambiar si el servidor tiene dónde guardar imágenes (en local, no)
     * @param cambiadoPor    quién hizo el último cambio; {@code null} si nunca cambió o se borró su cuenta
     */
    record FondoDeLaCarta(boolean cambiado, boolean sePuedeCambiar, String cambiadoPor, Instant cambiadoEn) {
    }

    record UrlDeSubida(URI url, String ruta) {
    }
}
