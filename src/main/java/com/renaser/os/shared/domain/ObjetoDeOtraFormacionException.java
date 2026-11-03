package com.renaser.os.shared.domain;

import java.util.NoSuchElementException;

/**
 * Lo pedido existe pero es de una formación que no es la de quien lo pide (FR-020, spec 022). Se responde como si no existiera
 * ({@code NoSuchElementException} → 404, con el mismo mensaje genérico): ni el texto ni el código revelan que está en otra formación.
 */
public class ObjetoDeOtraFormacionException extends NoSuchElementException {

    public static final String MENSAJE = "No se encontró lo que buscas.";

    public ObjetoDeOtraFormacionException() {
        super(MENSAJE);
    }
}
