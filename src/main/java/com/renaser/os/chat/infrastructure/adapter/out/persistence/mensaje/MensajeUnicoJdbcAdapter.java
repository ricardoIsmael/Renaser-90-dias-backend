package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import com.renaser.os.chat.application.ports.out.mensaje.GuardarMensajeUnicoPort;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * {@code INSERT ... ON CONFLICT (id) DO NOTHING} sobre {@code mensajes} (D-223). Con {@link JdbcClient} y no
 * con JPA porque {@code save} con un id que ya existe hace un {@code merge}: actualizaría el mensaje en vez
 * de dejarlo quieto. Lo que dice cuántas filas entraron es la base, así que dos instancias a la vez tampoco
 * duplican.
 *
 * <p>Solo lo usan mensajes del programa (sin respuesta ni duración): esas dos columnas quedan en null.
 */
@Component
class MensajeUnicoJdbcAdapter implements GuardarMensajeUnicoPort {

    private final JdbcClient jdbcClient;

    MensajeUnicoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean guardarSiNoExiste(Mensaje mensaje) {
        return jdbcClient.sql("""
                        INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto, media_bucket,
                                                      media_ruta, media_mime, media_bytes, creado_en)
                        VALUES (:id, :conversacion, :emisor, CAST(:tipo AS renaser.tipo_mensaje), :texto, :bucket,
                                :ruta, :mime, :bytes, :creado)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", mensaje.id().value())
                .param("conversacion", mensaje.conversacionId().value())
                .param("emisor", mensaje.emisorId().value())
                .param("tipo", mensaje.tipo().name())
                .param("texto", mensaje.texto())
                .param("bucket", mensaje.mediaBucket())
                .param("ruta", mensaje.mediaRuta())
                .param("mime", mensaje.mediaMime())
                .param("bytes", mensaje.mediaBytes())
                .param("creado", Timestamp.from(mensaje.creadoEn()))
                .update() == 1;
    }
}
