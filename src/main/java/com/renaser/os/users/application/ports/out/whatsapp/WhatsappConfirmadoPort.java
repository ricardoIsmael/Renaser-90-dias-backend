package com.renaser.os.users.application.ports.out.whatsapp;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * El WhatsApp que la cuenta confirmo con un codigo (spec 051), en {@code whatsapp_confirmados}: uno por cuenta y un numero en una sola cuenta.
 * La recuperacion de la contraseña lo prefiere al de la Ficha Inicial.
 */
public interface WhatsappConfirmadoPort {

    /** El numero confirmado de la cuenta ({@code +51987654321}), si lo hay. */
    Optional<String> de(UserId cuenta);

    /**
     * Guarda {@code numero} como el confirmado de la cuenta (reemplaza el anterior).
     *
     * @return {@code false} si ese numero ya esta confirmado en OTRA cuenta (no guarda nada)
     */
    boolean guardar(UserId cuenta, String numero);

    /** ¿Ese numero esta confirmado en una cuenta distinta de {@code excepto} ({@code null}: en cualquiera)? Spec 053. */
    boolean confirmadoPorOtra(String numero, UserId excepto);
}
