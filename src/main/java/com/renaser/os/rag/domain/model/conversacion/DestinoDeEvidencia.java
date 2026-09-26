package com.renaser.os.rag.domain.model.conversacion;

import java.util.Locale;

/**
 * A que se sube la foto de la tarjeta de la camara (D-178). La tarjeta nacio para los habitos de hoy
 * que exigen evidencia (D-171); desde D-178 tambien la pide una accion del dia (roca diaria), y la
 * app tiene que saber a que endpoints ir: {@code /habit-tracks/{id}/...} o {@code /rocks/{id}/...}.
 *
 * <p>Viaja en el JSON como {@link #valorJson()}. Una app anterior a D-178 no conoce el campo y trata
 * toda tarjeta como de habito: por eso el default es {@link #HABITO}, y los eventos de habitos salen
 * igual que antes (mas el campo, que esa app ignora).
 */
public enum DestinoDeEvidencia {

    /** {@code registroId} es el del registro del dia de un habito ({@code habit-tracks}). */
    HABITO,

    /** {@code registroId} es el de la roca diaria ({@code rocas_diarias}). */
    ROCA;

    /** {@code "habito"} o {@code "roca"}: el valor del campo {@code destino} del evento. */
    public String valorJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
