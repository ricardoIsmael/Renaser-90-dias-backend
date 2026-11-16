package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lectura por proyeccion (CLAUDE.MD §11): trae solo {@code clave_pregunta}, {@code valor_texto} y
 * {@code valor_escala} para las claves pedidas, en <b>una</b> consulta, sin materializar entidades.
 *
 * <p>El {@code JOIN} con {@code preguntas_onboarding} es lo que permite preguntar por la clave
 * estable en vez de por el id autoincremental — ver el javadoc del puerto.
 */
@Component
class RespuestasPorClaveJdbcAdapter implements LeerRespuestasPorClavePort {

    private static final String SQL = """
            SELECT p.clave_pregunta AS clave,
                   r.valor_texto    AS valor_texto,
                   r.valor_escala   AS valor_escala
              FROM renaser.respuestas_onboarding r
              JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
             WHERE r.usuario_id = :usuarioId
               AND p.clave_pregunta IN (:claves)
            """;

    /**
     * Una consulta para todas las personas pedidas. Usa el índice único {@code (usuario_id, pregunta_id)} de
     * {@code respuestas_onboarding}: la clave se resuelve por el índice único de {@code clave_pregunta} y las personas, por el
     * {@code IN}. Sin índice nuevo.
     */
    private static final String SQL_TEXTOS_DE_VARIOS = """
            SELECT r.usuario_id, r.valor_texto
              FROM renaser.respuestas_onboarding r
              JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
             WHERE p.clave_pregunta = :clave
               AND r.usuario_id IN (:usuarios)
               AND r.valor_texto IS NOT NULL
            """;

    private static final String SQL_OTRA_PERSONA_TIENE_TEXTO = """
            SELECT EXISTS (
                SELECT 1
                  FROM renaser.respuestas_onboarding r
                  JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
                 WHERE p.clave_pregunta = :clave
                   AND r.usuario_id <> :excepto
                   AND r.valor_texto IS NOT NULL
                   AND regexp_replace(r.valor_texto, '[\\s().-]', '', 'g') = :texto)
            """;

    private static final String SQL_MEDIA = """
            SELECT r.media_id
              FROM renaser.respuestas_onboarding r
              JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
             WHERE r.usuario_id = :usuarioId
               AND p.clave_pregunta = :clave
               AND r.media_id IS NOT NULL
            """;

    private final JdbcClient jdbcClient;

    RespuestasPorClaveJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<String, ValorDeRespuesta> deUsuario(UserId usuarioId, Set<String> clavesDePregunta) {
        if (clavesDePregunta == null || clavesDePregunta.isEmpty()) {
            return Map.of();
        }
        Map<String, ValorDeRespuesta> porClave = new HashMap<>();
        jdbcClient.sql(SQL)
                .param("usuarioId", usuarioId.value())
                .param("claves", clavesDePregunta)
                .query((rs, fila) -> {
                    Short escala = rs.getObject("valor_escala", Short.class);
                    porClave.put(rs.getString("clave"), new ValorDeRespuesta(rs.getString("valor_texto"), escala));
                    return null;
                })
                .list();
        return porClave;
    }

    @Override
    public Map<UserId, String> textosDeVarios(Collection<UserId> usuarios, String clavePregunta) {
        if (usuarios == null || usuarios.isEmpty()) {
            return Map.of();
        }
        Map<UserId, String> textos = new HashMap<>();
        jdbcClient.sql(SQL_TEXTOS_DE_VARIOS)
                .param("clave", clavePregunta)
                .param("usuarios", usuarios.stream().map(UserId::value).distinct().toList())
                .query((rs, fila) -> {
                    textos.put(UserId.of(rs.getObject("usuario_id", UUID.class)), rs.getString("valor_texto"));
                    return null;
                })
                .list();
        return textos;
    }

    @Override
    public boolean otraPersonaTieneTexto(String clavePregunta, String textoSinSeparadores, UserId excepto) {
        return Boolean.TRUE.equals(jdbcClient.sql(SQL_OTRA_PERSONA_TIENE_TEXTO)
                .param("clave", clavePregunta)
                .param("texto", textoSinSeparadores)
                .param("excepto", excepto == null ? new UUID(0L, 0L) : excepto.value())
                .query(Boolean.class)
                .single());
    }

    /** A lo sumo una fila: {@code clave_pregunta} es UNIQUE global y la respuesta, UNIQUE por (usuario, pregunta). */
    @Override
    public Optional<Long> mediaDe(UserId usuarioId, String clavePregunta) {
        return jdbcClient.sql(SQL_MEDIA)
                .param("usuarioId", usuarioId.value())
                .param("clave", clavePregunta)
                .query(Long.class)
                .optional();
    }
}
