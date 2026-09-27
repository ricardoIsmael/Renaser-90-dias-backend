package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;

/**
 * La marca de «a esta persona ya se le dio la bienvenida» (G-2, 2026-09-26). Vive en la tabla
 * {@code mensajes_bienvenida} del baseline (V1), que existía sin uso: una fila por destinatario
 * (PK natural) apuntando al primer mensaje de la bienvenida.
 *
 * <p>Es lo que hace idempotente la bienvenida: el outbox de Modulith puede reentregar el evento
 * (al reiniciar, o a los 5 minutos si quedó incompleto) y sin marca cada reentrega mandaba la
 * tarjeta otra vez.
 */
public interface MarcaDeBienvenidaPort {

    boolean yaSeDio(UserId destinatario);

    /**
     * Deja la marca. Se llama en la MISMA transacción que guarda los mensajes: o quedan los dos o
     * ninguno.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException si otra entrega ya la dejó (la
     *         PK de {@code usuario_destinatario_id}); la transacción de quien pierde se deshace entera
     */
    void marcar(UserId destinatario, MensajeId primerMensaje);
}
