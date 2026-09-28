package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.FotoDeCaja;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;

/**
 * La foto de la caja armada y el comprobante, en dos pasos (como la portada de la bienvenida, D-210): una URL
 * prefirmada para subirla directo al almacenamiento y la confirmación de la ruta. Sin almacenamiento de
 * verdad, confirmar responde 409.
 */
public interface FotosDeCajaUseCase {

    UrlDeSubida solicitarSubida(UserId actorId, UserId aprendizId, FotoDeCaja foto, String tipoContenido);

    DetalleDeCaja confirmar(UserId actorId, UserId aprendizId, FotoDeCaja foto, String ruta);

    /** @param ruta la que se confirma después (no la URL: esa lleva firma y vence) */
    record UrlDeSubida(URI url, String ruta) {
    }
}
