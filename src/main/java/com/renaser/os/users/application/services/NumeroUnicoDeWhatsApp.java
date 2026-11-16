package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException.Motivo;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import com.renaser.os.users.api.CodigoPorWhatsApp.NumeroParaCodigo;
import com.renaser.os.users.api.WhatsappEnOtraFicha;
import com.renaser.os.users.application.ports.out.whatsapp.NumeroEnSolicitudPendientePort;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import org.springframework.stereotype.Component;

/**
 * El número de WhatsApp es único (spec 051, D9 y D-353): un número «es de otra cuenta» si está confirmado en Yo por otra cuenta (spec 051), si otra
 * persona lo declaró en su Ficha Inicial o si hay una solicitud de acceso PENDIENTE de otra persona con ese número. Todo se compara en formato
 * internacional ({@code +51987654321}, el de {@link CodigoPorWhatsApp#numero}), así «+51 987 654 321» y «+51987654321» son el mismo.
 *
 * <p>Lo usan el paso del WhatsApp de «Solicitar acceso», la creación de la solicitud y su aprobación, que dicen lo mismo: 409 {@code NUMERO_EN_USO}.
 */
@Component
class NumeroUnicoDeWhatsApp {

    private final CodigoPorWhatsApp whatsapp;
    private final WhatsappConfirmadoPort confirmados;
    private final WhatsappEnOtraFicha fichas;
    private final NumeroEnSolicitudPendientePort pendientes;

    NumeroUnicoDeWhatsApp(CodigoPorWhatsApp whatsapp, WhatsappConfirmadoPort confirmados, WhatsappEnOtraFicha fichas,
                          NumeroEnSolicitudPendientePort pendientes) {
        this.whatsapp = whatsapp;
        this.confirmados = confirmados;
        this.fichas = fichas;
        this.pendientes = pendientes;
    }

    /** ¿Lo tiene otra cuenta distinta de {@code excepto} ({@code null}: todavía no hay cuenta)? */
    boolean loTieneOtraCuenta(NumeroParaCodigo numero, UserId excepto) {
        return confirmados.confirmadoPorOtra(numero.valor(), excepto)
                || fichas.loTieneOtraPersona(numero.valor(), excepto)
                || pendientes.hayPendienteDeOtra(numero.valor(), excepto);
    }

    /** @throws WhatsappDeLaCuentaException 409 {@code NUMERO_EN_USO} si lo tiene otra cuenta */
    void exigirLibre(NumeroParaCodigo numero, UserId excepto) {
        if (loTieneOtraCuenta(numero, excepto)) {
            throw new WhatsappDeLaCuentaException(Motivo.NUMERO_EN_USO);
        }
    }

    /**
     * Lo que la solicitud guardó como teléfono: sin número, o con algo que no tiene forma de WhatsApp (el servidor nunca lo validó al guardarlo), no
     * hay nada que comparar y no se rechaza.
     */
    void exigirLibreElTexto(String texto, UserId excepto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        whatsapp.numero(texto).ifPresent(numero -> exigirLibre(numero, excepto));
    }
}
