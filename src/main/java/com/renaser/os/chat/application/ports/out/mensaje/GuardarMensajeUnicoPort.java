package com.renaser.os.chat.application.ports.out.mensaje;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;

/**
 * Guarda un mensaje cuyo id se CALCULA (D-223), ignorando el choque con uno ya guardado con ese id. Es la
 * idempotencia de los mensajes del programa que salen de un barrido: el mensaje es su propia marca.
 *
 * <p>Aparte de {@link SaveMensajePort} y no un método más de ese puerto: aquel es el {@code save} de
 * siempre (un id repetido lo PISARÍA, porque para JPA un id conocido es una actualización), y hay pruebas
 * que lo implementan como lambda.
 */
public interface GuardarMensajeUnicoPort {

    /** @return {@code true} si lo guardó; {@code false} si ya había un mensaje con ese id (no toca nada) */
    boolean guardarSiNoExiste(Mensaje mensaje);
}
