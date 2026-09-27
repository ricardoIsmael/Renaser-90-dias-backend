package com.renaser.os.chat.infrastructure.adapter.out.persistence.bienvenida;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La marca de bienvenida (G-2) contra Postgres real, sobre {@code mensajes_bienvenida} del baseline:
 * una por destinatario, y la segunda choca con la PK (es lo que deshace una entrega cruzada).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MarcaDeBienvenidaJdbcAdapterTest {

    @Autowired
    private MarcaDeBienvenidaJdbcAdapter marca;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void unaMarcaPorDestinatarioYLaSegundaChoca() {
        UserId ana = usuario();
        UserId kelin = usuario();
        MensajeId tarjeta = mensaje(kelin);
        MensajeId otra = mensaje(kelin);

        assertThat(marca.yaSeDio(ana)).isFalse();
        marca.marcar(ana, tarjeta);
        assertThat(marca.yaSeDio(ana)).isTrue();
        assertThatThrownBy(() -> marca.marcar(ana, otra)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId usuario() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture bienvenida', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        return UserId.of(id);
    }

    private MensajeId mensaje(UserId emisor) {
        UUID conversacion = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO renaser.conversaciones (id, tipo, clave_directa) VALUES (:id, 'DIRECTA', :clave)")
                .param("id", conversacion).param("clave", "fixture:" + conversacion).update();
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto)
                        VALUES (:id, :conversacion, :emisor, 'TEXTO', 'Hola')
                        """)
                .param("id", id).param("conversacion", conversacion).param("emisor", emisor.value()).update();
        return MensajeId.of(id);
    }
}
