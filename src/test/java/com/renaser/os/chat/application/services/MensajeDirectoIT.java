package com.renaser.os.chat.application.services;

import com.renaser.os.EventosPendientes;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.api.MensajeDirectoPort;
import com.renaser.os.chat.api.MensajeDirectoPort.MensajeDirecto;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mensaje directo de un módulo a una persona (spec 056, FR-019, FR-025, FR-029), contra Postgres de verdad: lo que un doble no prueba es que la
 * conversación de dos se abre una sola vez (el UNIQUE de {@code clave_directa}), que los dos son participantes, que el mensaje queda guardado con su
 * texto y que el guardado publica el aviso que mueve el push.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MensajeDirectoIT {

    @Autowired private MensajeDirectoPort mensajeDirecto;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();
    private UUID lider;
    private UUID mentor;

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        lider = usuario("LIDER_MENTORES", "Lucía Líder");
        mentor = usuario("MENTOR", "Mateo Mentor");
    }

    @AfterEach
    void limpiar() {
        EventosPendientes.esperarQueTerminen(jdbc);
        for (UUID id : usuarios) {
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id IN (SELECT conversacion_id FROM renaser.participantes_conversacion WHERE usuario_id = ?)", id);
            jdbc.update("DELETE FROM renaser.formaciones_personal WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
        }
    }

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return id;
    }

    private int directasDeLosDos() {
        Integer n = jdbc.queryForObject("""
                SELECT count(DISTINCT c.id) FROM renaser.conversaciones c
                JOIN renaser.participantes_conversacion a ON a.conversacion_id = c.id AND a.usuario_id = ?
                JOIN renaser.participantes_conversacion b ON b.conversacion_id = c.id AND b.usuario_id = ?
                WHERE c.tipo = 'DIRECTA'
                """, Integer.class, lider, mentor);
        return n == null ? 0 : n;
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019 FR-025 FR-029: el primer mensaje abre el chat de dos y el segundo lo reutiliza; ambos quedan guardados con su texto")
    void abreUnaVezYReutiliza() {
        MensajeDirecto primero = mensajeDirecto.enviar(UserId.of(lider), UserId.of(mentor), "Mira la ficha de Ana");
        MensajeDirecto segundo = mensajeDirecto.enviar(UserId.of(lider), UserId.of(mentor), "¿Pudiste llamarla?");

        assertThat(segundo.conversacionId()).as("la misma conversación").isEqualTo(primero.conversacionId());
        assertThat(directasDeLosDos()).isEqualTo(1);
        List<UUID> participantes = jdbc.queryForList("SELECT usuario_id FROM renaser.participantes_conversacion WHERE conversacion_id = ?", UUID.class,
                primero.conversacionId());
        assertThat(participantes).containsExactlyInAnyOrder(lider, mentor);
        List<String> textos = jdbc.queryForList("SELECT texto FROM renaser.mensajes WHERE conversacion_id = ? ORDER BY creado_en", String.class,
                primero.conversacionId());
        assertThat(textos).containsExactly("Mira la ficha de Ana", "¿Pudiste llamarla?");
        UUID emisor = jdbc.queryForObject("SELECT emisor_id FROM renaser.mensajes WHERE id = ?", UUID.class, primero.mensajeId());
        assertThat(emisor).as("lo escribe quien manda, no el programa").isEqualTo(lider);
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019 FR-025: guardar el mensaje publica el aviso del chat (de ahí sale el push al mentor)")
    void elGuardadoPublicaElAvisoDelChat() {
        MensajeDirecto enviado = mensajeDirecto.enviar(UserId.of(lider), UserId.of(mentor), "Buen trabajo con el grupo");

        Integer avisos = jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE event_type LIKE '%MensajeDeChatGuardadoEvent' AND serialized_event LIKE ?",
                Integer.class, "%" + enviado.mensajeId() + "%");
        assertThat(avisos).isEqualTo(1);
    }
}
