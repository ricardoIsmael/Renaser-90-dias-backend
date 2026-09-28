package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.caja;

import com.renaser.os.onboarding.application.ports.out.caja.ContenidoDeCajaPort;
import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.ElementoDeCaja;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * El contenido de la caja sobre el motor de formularios (V82): la lista es {@code opciones_pregunta} de la
 * pregunta {@code caja_contenido}, y el checklist de cada aprendiz su fila de {@code respuestas_onboarding}
 * (UPSERT por el UNIQUE (usuario_id, pregunta_id) del baseline, con el array en {@code valor_json}).
 */
@Component
class ContenidoDeCajaJdbcAdapter implements ContenidoDeCajaPort {

    static final String CLAVE = "caja_contenido";

    private static final String PREGUNTA = "(SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = '"
            + CLAVE + "')";
    private static final String OPCIONES = "SELECT valor, etiqueta FROM renaser.opciones_pregunta WHERE pregunta_id = "
            + PREGUNTA + " ORDER BY orden";
    private static final String BORRAR_OPCIONES = "DELETE FROM renaser.opciones_pregunta WHERE pregunta_id = "
            + PREGUNTA;
    private static final String INSERTAR_OPCION = "INSERT INTO renaser.opciones_pregunta (pregunta_id, orden, valor, "
            + "etiqueta) VALUES (" + PREGUNTA + ", :orden, :valor, :etiqueta)";
    private static final String MARCADOS = """
            SELECT r.valor_json::text FROM renaser.respuestas_onboarding r
             WHERE r.usuario_id = :usuario AND r.pregunta_id = """ + PREGUNTA;
    private static final String GUARDAR_MARCADOS = """
            INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_json, respondida_en, actualizado_en)
            VALUES (:usuario, """ + PREGUNTA + """
            , CAST(:json AS jsonb), :en, :en)
            ON CONFLICT (usuario_id, pregunta_id) DO UPDATE
               SET valor_json = EXCLUDED.valor_json, actualizado_en = EXCLUDED.actualizado_en
            """;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;

    ContenidoDeCajaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ContenidoDeCaja vigente() {
        return new ContenidoDeCaja(jdbcClient.sql(OPCIONES)
                .query((rs, n) -> new ElementoDeCaja(rs.getString("valor"), rs.getString("etiqueta"))).list());
    }

    @Override
    public void reemplazar(ContenidoDeCaja contenido) {
        jdbcClient.sql(BORRAR_OPCIONES).update();
        List<ElementoDeCaja> elementos = contenido.elementos();
        for (int orden = 0; orden < elementos.size(); orden++) {
            jdbcClient.sql(INSERTAR_OPCION).param("orden", (short) orden)
                    .param("valor", elementos.get(orden).valor()).param("etiqueta", elementos.get(orden).etiqueta())
                    .update();
        }
    }

    @Override
    public Set<String> marcadosDe(UserId aprendizId) {
        return jdbcClient.sql(MARCADOS).param("usuario", aprendizId.value()).query(String.class).optional()
                .map(json -> (Set<String>) new TreeSet<>(JSON.readValue(json, LISTA)))
                .orElseGet(TreeSet::new);
    }

    @Override
    public void guardarMarcados(UserId aprendizId, Set<String> marcados, Instant en) {
        jdbcClient.sql(GUARDAR_MARCADOS).param("usuario", aprendizId.value())
                .param("json", JSON.writeValueAsString(new TreeSet<>(marcados)))
                .param("en", Timestamp.from(en)).update();
    }
}
