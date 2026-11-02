package com.renaser.os.habits.infrastructure.adapter.out.persistence.medicion;

import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link MetaDeMedicionPort} con {@link JdbcClient}: una sola columna ({@code habitos.meta_medicion}, V161, spec 037) que
 * el agregado {@code Habito} no conoce a propósito (ver el puerto). Dos consultas de una fila por clave primaria.
 */
@Component
class MetaDeMedicionJdbcAdapter implements MetaDeMedicionPort {

    private final JdbcClient jdbcClient;

    MetaDeMedicionJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<BigDecimal> metaDe(HabitoId habitoId) {
        List<BigDecimal> filas = jdbcClient.sql("SELECT meta_medicion FROM renaser.habitos WHERE id = :id")
                .param("id", habitoId.value())
                .query((rs, fila) -> rs.getBigDecimal("meta_medicion"))
                .list();
        return filas.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    public void fijar(HabitoId habitoId, BigDecimal meta, Instant ahora) {
        jdbcClient.sql("UPDATE renaser.habitos SET meta_medicion = :meta, actualizado_en = :ahora WHERE id = :id")
                .param("meta", meta, java.sql.Types.NUMERIC)
                .param("ahora", Timestamp.from(ahora))
                .param("id", habitoId.value())
                .update();
    }
}
