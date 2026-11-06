package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.application.ports.out.respuesta.SembrarRespuestaPorClavePort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Una sola sentencia: resuelve la pregunta por su clave estable y no escribe nada si la persona ya respondio (el UNIQUE {@code (usuario_id, pregunta_id)}
 * de {@code respuestas_onboarding}). Sin la pregunta, no inserta ninguna fila.
 */
@Component
class SembrarRespuestaPorClaveJdbcAdapter implements SembrarRespuestaPorClavePort {

    private static final String SQL = """
            INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto, respondida_en, actualizado_en)
            SELECT :usuarioId, p.id, :texto, :cuando, :cuando
              FROM renaser.preguntas_onboarding p
             WHERE p.clave_pregunta = :clave
            ON CONFLICT (usuario_id, pregunta_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    SembrarRespuestaPorClaveJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean siNoHay(UserId persona, String clavePregunta, String texto, Instant cuando) {
        return jdbcClient.sql(SQL)
                .param("usuarioId", persona.value())
                .param("texto", texto)
                .param("cuando", Timestamp.from(cuando))
                .param("clave", clavePregunta)
                .update() > 0;
    }
}
