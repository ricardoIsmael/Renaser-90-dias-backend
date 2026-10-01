package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-471: el último mensaje de cada conversación (lo que muestra la lista de chats) contra Postgres real.
 *
 * <p>La consulta pasó de {@code DISTINCT ON ... IN (...)}, que leía y ordenaba TODOS los mensajes de
 * las conversaciones pedidas, a una búsqueda {@code LATERAL ... LIMIT 1} por conversación sobre el
 * índice {@code mensajes_conversacion_idx}. Esta prueba exige que el resultado sea EXACTAMENTE el de la
 * consulta vieja (que se corre acá tal cual era), con una conversación de miles de mensajes, otras
 * chicas, una vacía, un id inexistente y un último mensaje borrado/oculto (no se filtra, igual que
 * antes); y que el plan use el índice.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UltimoMensajePorConversacionIT {

    /** La consulta anterior a E-471, literal: es la referencia del resultado. */
    private static final String CONSULTA_VIEJA = """
            SELECT DISTINCT ON (conversacion_id) conversacion_id, id
            FROM renaser.mensajes
            WHERE conversacion_id IN (%s)
            ORDER BY conversacion_id, creado_en DESC
            """;

    @Autowired
    private LoadMensajePort loadMensajePort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> conversaciones = new ArrayList<>();
    private UUID autor;

    @BeforeEach
    void seed() {
        autor = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Autor E-471', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                """, autor, autor + "@renaser.test");
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", autor);
    }

    @Test
    @DisplayName("E-471: el último mensaje por conversación es el mismo que daba la consulta vieja, con miles de mensajes")
    void mismoResultadoQueLaConsultaVieja() {
        UUID grande = conversacionCon(5_000);
        UUID mediana = conversacionCon(40);
        UUID deUno = conversacionCon(1);
        UUID vacia = conversacionCon(0);
        UUID conUltimoBorrado = conversacionCon(10);
        jdbcTemplate.update("""
                INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto, oculto, eliminado_en, creado_en)
                VALUES (?, ?, 'borrado y oculto', true, now(), now() + interval '1 minute')
                """, conUltimoBorrado, autor);
        UUID inexistente = UUID.randomUUID();
        List<UUID> pedidas = List.of(grande, mediana, deUno, vacia, conUltimoBorrado, inexistente);

        Map<ConversacionId, Mensaje> nuevos = loadMensajePort.ultimosPorConversacion(
                pedidas.stream().map(ConversacionId::of).toList());

        Map<UUID, UUID> esperado = consultaVieja(pedidas);
        Map<UUID, UUID> obtenido = new HashMap<>();
        nuevos.forEach((conversacion, mensaje) -> obtenido.put(conversacion.value(), mensaje.id().value()));
        assertThat(obtenido).isEqualTo(esperado);
        assertThat(obtenido).as("sin mensajes, o inexistente: sin fila").doesNotContainKeys(vacia, inexistente);
        assertThat(obtenido).containsKeys(grande, mediana, deUno, conUltimoBorrado);
        assertThat(nuevos.get(ConversacionId.of(grande)).texto()).isEqualTo("mensaje 0");
    }

    @Test
    @DisplayName("E-471: una lista vacía no consulta y devuelve vacío")
    void listaVacia() {
        assertThat(loadMensajePort.ultimosPorConversacion(List.of())).isEmpty();
    }

    @Test
    @DisplayName("E-471: el plan busca por el índice (conversacion_id, creado_en), no recorre y ordena la tabla")
    void elPlanUsaElIndice() {
        UUID grande = conversacionCon(5_000);
        UUID chica = conversacionCon(30);
        jdbcTemplate.execute("ANALYZE renaser.mensajes");

        // La consulta que corre el repositorio, con los ids escritos en lugar del parámetro.
        String consulta = SpringDataMensajeRepository.ULTIMOS_POR_CONVERSACION
                .replace(":conversacionIds", "'" + grande + "', '" + chica + "'");
        String plan = jdbcTemplate.queryForList("EXPLAIN " + consulta, String.class).stream()
                .collect(Collectors.joining("\n"));

        assertThat(plan).contains("mensajes_conversacion_idx");
        assertThat(plan).doesNotContain("Seq Scan on mensajes");
    }

    private Map<UUID, UUID> consultaVieja(List<UUID> ids) {
        String lista = ids.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "));
        Map<UUID, UUID> resultado = new HashMap<>();
        jdbcTemplate.query(CONSULTA_VIEJA.formatted(lista), fila -> {
            resultado.put(fila.getObject("conversacion_id", UUID.class), fila.getObject("id", UUID.class));
        });
        return resultado;
    }

    /** Una conversación DIRECTA con {@code cuantos} mensajes; el más nuevo es «mensaje 0». */
    private UUID conversacionCon(int cuantos) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa)
                VALUES (?, CAST('DIRECTA' AS renaser.tipo_conversacion), ?)
                """, id, "e471|" + id);
        conversaciones.add(id);
        if (cuantos > 0) {
            jdbcTemplate.update("""
                    INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto, creado_en)
                    SELECT ?, ?, 'mensaje ' || i, now() - i * interval '1 second'
                    FROM generate_series(0, ? - 1) AS i
                    """, id, autor, cuantos);
        }
        return id;
    }
}
