package com.renaser.os.chat.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Lo que otro módulo necesita para escribirle a una persona en su chat directo (spec 056, FR-019, FR-025 y FR-029): el líder avisa a un mentor, le deja una
 * observación o le recuerda una consulta, y el mensaje le llega a su chat con el líder. Lo resuelve {@code chat}, que es quien sabe abrir o reutilizar la
 * conversación de dos y guardar el mensaje; así {@code leadership} y {@code support} no importan nada de {@code chat.application}.
 *
 * <p>El mensaje lo escribe <b>quien manda</b> (no el programa) y entra al chat como cualquier otro de texto: respeta los interruptores de Soporte TI y
 * las formaciones en común, y <b>su guardado ya publica el aviso del chat</b> ({@link MensajeDeChatGuardadoEvent}), de donde sale el push al
 * destinatario. Quien llama no manda un segundo push por el mismo mensaje.
 *
 * <p>Corre en la transacción de quien llama, si hay una: si esta se deshace, el mensaje también, y el aviso del chat con él. Un rechazo del chat (los
 * chats directos apagados en esa formación, dos personas sin formación en común, una cuenta suspendida) sube como la excepción de siempre, con su motivo.
 */
public interface MensajeDirectoPort {

    /**
     * Abre —o reutiliza— la conversación de dos entre {@code remitente} y {@code destinatario} y guarda ahí un mensaje de texto de {@code remitente}.
     *
     * @return dónde quedó el mensaje: sirve para llevar el aviso al chat exacto
     * @throws IllegalArgumentException si el texto está en blanco o es demasiado largo, o si las dos personas son la misma
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si el chat directo no está disponible o alguna de las cuentas no está activa
     * @throws java.util.NoSuchElementException si las dos personas no comparten ninguna formación (se responde como si la otra no existiera)
     */
    MensajeDirecto enviar(UserId remitente, UserId destinatario, String texto);

    /** Dónde quedó el mensaje: la conversación de dos y su id. */
    record MensajeDirecto(UUID conversacionId, UUID mensajeId) {
    }
}
