package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje.MensajeResponse;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La bienvenida del soporte firmada por el programa (D-199) contra Postgres de verdad: la base acepta un
 * {@code SISTEMA} a nombre del aprendiz sin tocar el esquema, la marca queda con él, y el listado lo
 * devuelve firmado por el programa. En las pruebas el almacenamiento es de marcador, así que sale solo
 * el mensaje formal (G-5). Con el interruptor PRENDIDO: apagado por defecto no manda nada.
 */
@SpringBootTest(properties = "renaser.chat.bienvenida.activa=true")
@Import(TestcontainersConfiguration.class)
class BienvenidaEnSoporteIT {

    @Autowired
    private DarBienvenidaEnSoporteUseCase darBienvenida;
    @Autowired
    private ListarMensajesUseCase listarMensajes;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID aprendiz;
    private UUID soporte;

    @BeforeEach
    void seed() {
        aprendiz = UUID.randomUUID();
        soporte = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Ana Pérez', 'APRENDIZ', 'ACTIVO')
                """, aprendiz, aprendiz + "@renaser.test");
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre)
                VALUES (?, 'SOPORTE', ?, 'Ana – Formación Renaser')
                """, soporte, "soporte:" + aprendiz);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id, ultimo_leido_en)
                VALUES (?, ?, now() - interval '1 hour')
                """, soporte, aprendiz);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", soporte);
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", aprendiz);
    }

    @Test
    @DisplayName("D-199: sale un SISTEMA a nombre de la aprendiz, con la marca, sin duplicarse; el listado lo firma el programa con el UUID nulo")
    void elProgramaDaLaBienvenidaEnElSoporte() {
        darBienvenida.darBienvenida(ConversacionId.of(soporte), UserId.of(aprendiz));
        darBienvenida.darBienvenida(ConversacionId.of(soporte), UserId.of(aprendiz));

        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "SELECT id, tipo::text AS tipo, emisor_id, texto FROM renaser.mensajes WHERE conversacion_id = ?", soporte);
        assertThat(filas).as("solo el formal, y la reentrega no lo repite").singleElement().satisfies(fila -> {
            assertThat(fila.get("tipo")).isEqualTo("SISTEMA");
            assertThat(fila.get("emisor_id")).isEqualTo(aprendiz);
            assertThat((String) fila.get("texto")).contains("Ana").doesNotContain("{nombre}");
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT mensaje_id FROM renaser.mensajes_bienvenida WHERE usuario_destinatario_id = ?", UUID.class,
                aprendiz)).isEqualTo(filas.get(0).get("id"));

        MensajeResponse cable = MensajeResponse.from(listarMensajes.listar(UserId.of(aprendiz), ConversacionId.of(soporte),
                null, 30).mensajes().get(0));
        assertThat(cable.type()).isEqualTo("SYSTEM");
        assertThat(cable.senderId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(cable.senderName()).isEqualTo("Formación Renaser");
        assertThat(cable.senderAvatarUrl()).isNull();
    }
}
