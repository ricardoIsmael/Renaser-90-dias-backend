package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Llevar a la Ficha Inicial el WhatsApp que la persona escribio al solicitar acceso (spec 043, D-325, FR-006). <b>Lo implementa {@code onboarding}</b>,
 * dueño de {@code respuestas_onboarding}, y lo llama {@code users} al aceptar la solicitud, dentro de la misma transaccion (el mismo patron de
 * {@link FichaAlAceptar}).
 *
 * <p>El dato sigue viviendo en UN solo lugar, la respuesta {@code whatsapp} de la ficha: la misma que lee la Caja, el panel de Administracion, los
 * recordatorios de eventos (spec 041) y la recuperacion de la contraseña por WhatsApp. Nunca pisa un numero que la persona ya tenga.
 */
public interface WhatsappDelRegistro {

    /**
     * Deja {@code whatsapp} como la respuesta {@code whatsapp} de la ficha de {@code persona} si todavia no tiene una. No hace nada si lo escrito no tiene
     * forma de numero, o si la ficha no tiene esa pregunta.
     */
    void llevarALaFicha(UserId persona, String whatsapp);
}
