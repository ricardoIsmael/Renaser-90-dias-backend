package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * ¿Otra persona declaró ya este WhatsApp en su Ficha Inicial? (spec 051: el número es único). <b>Lo implementa {@code onboarding}</b>, dueño de
 * {@code respuestas_onboarding}, y lo consume {@code users} al pedir acceso y al aprobarlo: la dependencia va de {@code onboarding} hacia
 * {@code users}, como siempre (el mismo patrón de {@link WhatsappDeLaFichaFinder}). Compara sin espacios, paréntesis ni guiones, porque la ficha
 * guarda {@code "+51 987654321"} tal como lo escribió la persona.
 */
public interface WhatsappEnOtraFicha {

    /**
     * @param numeroInternacional el número en formato internacional sin separadores ({@code +51987654321})
     * @param excepto             la cuenta que no cuenta como «otra» (la que aprueba), o {@code null} si todavía no hay cuenta
     */
    boolean loTieneOtraPersona(String numeroInternacional, UserId excepto);
}
