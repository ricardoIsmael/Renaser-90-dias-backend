package com.renaser.os.chat.infrastructure.adapter.out.persistence.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code mensajes_bienvenida} (V1) con {@link JdbcClient}: dos sentencias de una línea no justifican
 * una entidad JPA. El INSERT es plano, sin {@code ON CONFLICT}, a propósito: si otra entrega ya dejó
 * la marca, la violación de la PK deshace la transacción que acaba de guardar mensajes repetidos.
 */
@Component
class MarcaDeBienvenidaJdbcAdapter implements MarcaDeBienvenidaPort {

    private final JdbcClient jdbcClient;

    MarcaDeBienvenidaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean yaSeDio(UserId destinatario) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM renaser.mensajes_bienvenida "
                        + "WHERE usuario_destinatario_id = :destinatario)")
                .param("destinatario", destinatario.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void marcar(UserId destinatario, MensajeId primerMensaje) {
        jdbcClient.sql("INSERT INTO renaser.mensajes_bienvenida (usuario_destinatario_id, mensaje_id) "
                        + "VALUES (:destinatario, :mensaje)")
                .param("destinatario", destinatario.value())
                .param("mensaje", primerMensaje.value())
                .update();
    }
}
