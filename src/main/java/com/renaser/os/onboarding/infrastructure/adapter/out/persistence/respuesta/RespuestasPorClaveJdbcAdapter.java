package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
}
