package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.ficha;

import com.renaser.os.onboarding.application.ports.out.ficha.RespuestasDelDiagnosticoPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Una sola consulta de existencia sobre las tablas del propio módulo. */
@Component
class RespuestasDelDiagnosticoJdbcAdapter implements RespuestasDelDiagnosticoPort {

    private static final String SQL = """
            SELECT EXISTS (
                SELECT 1
                  FROM renaser.respuestas_onboarding r
                  JOIN renaser.preguntas_onboarding  p ON p.id = r.pregunta_id
                  JOIN renaser.secciones_onboarding  s ON s.id = p.seccion_id
                 WHERE r.usuario_id = :usuarioId
                   AND s.flujo = :flujo)
            """;

    private final JdbcClient jdbcClient;

    RespuestasDelDiagnosticoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean hayRespuestas(UserId persona) {
        return Boolean.TRUE.equals(jdbcClient.sql(SQL)
                .param("usuarioId", persona.value())
                .param("flujo", Diagnostico.FLUJO)
                .query(Boolean.class)
                .single());
    }
}
