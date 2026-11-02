package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.PortadaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.TextoEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.UltimoCambio;

import java.time.Instant;
import java.util.List;

/**
 * La bienvenida para editarla (D-210), como la lee la app. Mapeo a mano y campo por campo: un campo
 * nuevo del caso de uso no llega al teléfono sin que alguien lo agregue acá.
 *
 * @param formation el código de la formación de esta bienvenida (spec 035)
 * @param textos     en el orden en que salen: SOPORTE_CON_LA_TARJETA, SOPORTE_FORMAL, GRUPO
 */
record BienvenidaResponse(String formation, boolean activa, int largoMaximo, List<TextoResponse> textos, PortadaResponse portada) {

    static BienvenidaResponse from(BienvenidaEditable bienvenida) {
        return new BienvenidaResponse(bienvenida.formacion().codigo(), bienvenida.activa(), bienvenida.largoMaximo(),
                bienvenida.textos().stream().map(TextoResponse::from).toList(),
                PortadaResponse.from(bienvenida.portada()));
    }

    /**
     * @param clave        la de la ruta {@code /textos/{clave}}
     * @param texto        el que sale hoy
     * @param original     el del repo, al que se vuelve
     * @param cambiado     si hoy sale uno guardado desde la app
     * @param ultimoCambio {@code null} si nunca se cambió
     */
    record TextoResponse(String clave, String texto, String original, boolean cambiado, List<String> marcadores,
                         UltimoCambioResponse ultimoCambio) {

        static TextoResponse from(TextoEditable texto) {
            return new TextoResponse(texto.pieza().name(), texto.texto(), texto.original(), texto.cambiado(),
                    texto.marcadores(), UltimoCambioResponse.from(texto.ultimoCambio()));
        }
    }

    /**
     * @param heredada       {@code true} si la formación no tiene portada propia y usa la vigente de la Escuela (spec 035); siempre {@code false} en la Escuela
     * @param sePuedeCambiar {@code false} si el servidor no guarda imágenes (almacenamiento de marcador)
     */
    record PortadaResponse(boolean cambiada, boolean heredada, boolean sePuedeCambiar, UltimoCambioResponse ultimoCambio) {

        static PortadaResponse from(PortadaEditable portada) {
            return new PortadaResponse(portada.cambiada(), portada.heredada(), portada.sePuedeCambiar(),
                    UltimoCambioResponse.from(portada.ultimoCambio()));
        }
    }

    /** @param por {@code null} si la cuenta de quien lo cambió ya no existe */
    record UltimoCambioResponse(String por, Instant en, boolean volvioAlOriginal) {

        static UltimoCambioResponse from(UltimoCambio cambio) {
            return cambio == null ? null : new UltimoCambioResponse(cambio.por(), cambio.en(), cambio.volvioAlOriginal());
        }
    }
}
