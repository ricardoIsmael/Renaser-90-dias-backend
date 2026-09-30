package com.renaser.os.rag.infrastructure.adapter.out.onboarding;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-233 de punta a punta contra Postgres real: las respuestas del flujo {@code mapa_dia7} sembradas por la
 * V41 (texto y escala), las acciones y la marca de etapa, leidas por {@code onboarding.api} y traducidas al
 * tipo de {@code rag}. Lo que no se puede probar con dobles: que las claves de la V41 y los slots del EAV
 * sean los que el finder pide.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConsultarMapaDeRenacimientoIT {

    @Autowired
    private ConsultarMapaDeRenacimientoPort port;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID conMapa;
    private UUID sinMapa;

    @BeforeEach
    void sembrar() {
        conMapa = usuario();
        sinMapa = usuario();
        texto("map_priority_area", "salud");
        texto("map_health_result_type", "peso");
        texto("map_health_baseline", "92");
        texto("map_health_target_day90", "85");
        texto("map_health_unit", "kg");
        texto("map_health_reason", "quiero jugar con mis hijos");
        texto("map_health_goal_text", "Bajar de 92 a 85 kg al dia 90");
        texto("map_relations_bond", "pareja");
        texto("map_milestone_health_30", "89 kg");
        texto("map_return_protocol", "caminar 10 minutos");
        jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_escala)
                VALUES (?, (SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?), ?)
                """, conMapa, "map_relations_baseline_scale", 5);
        jdbc.update("""
                INSERT INTO renaser.acciones_mapa (id, usuario_id, accion_id, area, texto, frecuencia_semanal)
                VALUES (?, ?, 'a1', 'salud', 'Caminar 40 minutos', 5)
                """, UUID.randomUUID(), conMapa);
        jdbc.update("INSERT INTO renaser.etapas_onboarding_completadas (usuario_id, flujo) VALUES (?, 'mapa_dia7')",
                conMapa);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?)", conMapa, sinMapa);
    }

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')", id, id + "@prueba.test");
        return id;
    }

    private void texto(String clave, String valor) {
        jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                VALUES (?, (SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?), ?)
                """, conMapa, clave, valor);
    }

    @Test
    @DisplayName("lee el Mapa real: prioridad, objetivo con linea base y meta, relaciones en escala, hito, retorno y accion")
    void mapaReal() {
        MapaDeLaPersona mapa = port.de(UserId.of(conMapa));

        assertThat(mapa.tieneMapa()).isTrue();
        assertThat(mapa.completado()).isTrue();
        assertThat(mapa.prioridad()).isEqualTo("salud");
        assertThat(mapa.objetivos()).containsExactly(
                new MapaDeLaPersona.Objetivo("salud", "peso", "92", "85", "kg", null, null, null,
                        "quiero jugar con mis hijos", "Bajar de 92 a 85 kg al dia 90"),
                new MapaDeLaPersona.Objetivo("relaciones", "pareja", "5", null, "de 10", null, null, null, null, null));
        assertThat(mapa.hitos()).containsExactly(new MapaDeLaPersona.Hito("salud", 30, "89 kg"));
        assertThat(mapa.protocoloDeRetorno()).isEqualTo("caminar 10 minutos");
        assertThat(mapa.acciones()).containsExactly(new MapaDeLaPersona.Accion("salud", "Caminar 40 minutos", 5));
        assertThat(mapa.reemplazos()).isEqualTo(List.of());
    }

    @Test
    @DisplayName("quien no recorrio el Mapa sale como 'sin mapa', sin error")
    void sinMapa() {
        assertThat(port.de(UserId.of(sinMapa))).isEqualTo(MapaDeLaPersona.sinMapa());
    }
}
