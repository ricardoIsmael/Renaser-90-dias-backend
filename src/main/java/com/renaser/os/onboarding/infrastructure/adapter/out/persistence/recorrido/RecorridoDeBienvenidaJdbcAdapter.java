package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.recorrido;

import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase.ComoTermino;
import com.renaser.os.onboarding.application.ports.out.recorrido.RecorridoDeBienvenidaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** Spec 045: `recorrido_de_bienvenida` (V180). Un recorrido terminado no se reabre ni se cambia. */
@Component
class RecorridoDeBienvenidaJdbcAdapter implements RecorridoDeBienvenidaPort {

    private final JdbcClient jdbcClient;

    RecorridoDeBienvenidaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<FilaDelRecorrido> de(UserId persona) {
        return jdbcClient.sql("SELECT paso, terminado_en, como FROM renaser.recorrido_de_bienvenida WHERE usuario_id = :id")
                .param("id", persona.value())
                .query((fila, n) -> {
                    Timestamp en = fila.getTimestamp("terminado_en");
                    String como = fila.getString("como");
                    return new FilaDelRecorrido(fila.getInt("paso"), en == null ? null : en.toInstant(),
                            como == null ? null : ComoTermino.valueOf(como));
                })
                .optional();
    }

    @Override
    public void guardarPaso(UserId persona, int paso, Instant ahora) {
        jdbcClient.sql("""
                        INSERT INTO renaser.recorrido_de_bienvenida (usuario_id, paso, actualizado_en)
                        VALUES (:id, :paso, :ahora)
                        ON CONFLICT (usuario_id) DO UPDATE SET paso = EXCLUDED.paso, actualizado_en = EXCLUDED.actualizado_en
                         WHERE renaser.recorrido_de_bienvenida.terminado_en IS NULL
                        """)
                .param("id", persona.value())
                .param("paso", paso)
                .param("ahora", Timestamp.from(ahora))
                .update();
    }

    @Override
    public void terminar(UserId persona, ComoTermino como, Instant ahora) {
        jdbcClient.sql("""
                        INSERT INTO renaser.recorrido_de_bienvenida (usuario_id, paso, terminado_en, como, actualizado_en)
                        VALUES (:id, 5, :ahora, :como, :ahora)
                        ON CONFLICT (usuario_id) DO UPDATE
                           SET terminado_en = EXCLUDED.terminado_en, como = EXCLUDED.como, actualizado_en = EXCLUDED.actualizado_en
                         WHERE renaser.recorrido_de_bienvenida.terminado_en IS NULL
                        """)
                .param("id", persona.value())
                .param("ahora", Timestamp.from(ahora))
                .param("como", como.name())
                .update();
    }
}
