package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Como tratar a la persona en lo que se le escribe: lo que declaro en la pregunta {@code sex} de la
 * ficha inicial (SELECCION_UNICA, opciones "Masculino" y "Femenino", E-457).
 *
 * <p>Nace para el acompanante: con el prompt solo, flash-lite igual escribia "dejas de ser reactiva" a
 * un hombre al parafrasear el material. Sin respuesta, o con cualquier otro valor, es NEUTRO: nunca se
 * adivina el genero.
 */
public interface TratoDeLaPersonaFinder {

    /** Nunca {@code null} y nunca falla por no haber respondido: sin dato es {@link Trato#NEUTRO}. */
    Trato de(UserId usuarioId);

    enum Trato {
        MASCULINO, FEMENINO, NEUTRO
    }
}
