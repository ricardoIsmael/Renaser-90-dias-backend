package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.caja;

import com.renaser.os.onboarding.application.ports.out.caja.FichaDeEnvioPort;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Los datos de envío en UNA consulta para todos los aprendices pedidos (proyección, como
 * {@code RespuestasPorClaveJdbcAdapter}): las respuestas de texto de las claves de {@link FichaDeEnvio#CLAVES}.
 * El destino alternativo se escribe como las respuestas de cualquier formulario: UPSERT por
 * (usuario_id, pregunta_id), y una respuesta vacía se borra.
 */
@Component
class FichaDeEnvioJdbcAdapter implements FichaDeEnvioPort {

    private static final String LEER = """
            SELECT r.usuario_id, p.clave_pregunta, r.valor_texto
              FROM renaser.respuestas_onboarding r
              JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
             WHERE r.usuario_id IN (:usuarios)
               AND p.clave_pregunta IN (:claves)
               AND r.valor_texto IS NOT NULL
            """;
    private static final String GUARDAR = """
            INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto, respondida_en, actualizado_en)
            SELECT :usuario, p.id, :texto, :en, :en FROM renaser.preguntas_onboarding p WHERE p.clave_pregunta = :clave
            ON CONFLICT (usuario_id, pregunta_id) DO UPDATE
               SET valor_texto = EXCLUDED.valor_texto, actualizado_en = EXCLUDED.actualizado_en
            """;
    private static final String BORRAR = """
            DELETE FROM renaser.respuestas_onboarding r
             USING renaser.preguntas_onboarding p
             WHERE p.id = r.pregunta_id AND r.usuario_id = :usuario AND p.clave_pregunta = :clave
            """;

    private final JdbcClient jdbcClient;

    FichaDeEnvioJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<UserId, FichaDeEnvio> de(Collection<UserId> aprendices) {
        Map<UserId, Map<String, String>> respuestas = new HashMap<>();
        if (!aprendices.isEmpty()) {
            jdbcClient.sql(LEER)
                    .param("usuarios", aprendices.stream().map(UserId::value).toList())
                    .param("claves", FichaDeEnvio.CLAVES)
                    .query((rs, n) -> {
                        UserId id = UserId.of(rs.getObject("usuario_id", UUID.class));
                        respuestas.computeIfAbsent(id, x -> new HashMap<>())
                                .put(rs.getString("clave_pregunta"), rs.getString("valor_texto").strip());
                        return null;
                    }).list();
        }
        Map<UserId, FichaDeEnvio> fichas = new HashMap<>();
        aprendices.forEach(id -> fichas.put(id, FichaDeEnvio.desde(respuestas.getOrDefault(id, Map.of()))));
        return fichas;
    }

    @Override
    public void guardarDestino(UserId aprendizId, DestinoAlternativo destino, Instant en) {
        destino.porClave().forEach((clave, texto) -> {
            if (texto == null) {
                jdbcClient.sql(BORRAR).param("usuario", aprendizId.value()).param("clave", clave).update();
            } else {
                jdbcClient.sql(GUARDAR).param("usuario", aprendizId.value()).param("texto", texto)
                        .param("en", Timestamp.from(en)).param("clave", clave).update();
            }
        });
    }
}
