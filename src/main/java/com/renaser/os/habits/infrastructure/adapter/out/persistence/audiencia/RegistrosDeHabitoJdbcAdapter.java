package com.renaser.os.habits.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.habits.application.ports.out.audiencia.ContarRegistrosDeHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Antes de eliminar un hábito: ¿tiene historia? Tabla propia de habits, un {@code EXISTS} por el índice de la FK. */
@Component
class RegistrosDeHabitoJdbcAdapter implements ContarRegistrosDeHabitoPort {

    private static final String SQL = """
            SELECT EXISTS (SELECT 1 FROM renaser.registros_habito WHERE habito_id = :habito)
            """;

    private final JdbcClient jdbcClient;

    RegistrosDeHabitoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean tieneRegistros(HabitoId habito) {
        return Boolean.TRUE.equals(jdbcClient.sql(SQL).param("habito", habito.value()).query(Boolean.class).single());
    }
}
