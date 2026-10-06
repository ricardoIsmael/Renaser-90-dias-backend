package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.application.ports.out.registro.ConsultarPendientesVencidosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@link ConsultarPendientesVencidosPort} con {@link JdbcClient}: una consulta agrupada por pagina, sobre una tabla de
 * {@code habits} (D-41). Usa {@code registros_estado_idx (estado, fecha_ejecucion)}, el indice que el baseline creo
 * para este barrido ("expiracion por lotes del cron"). Sin migracion.
 *
 * <p>Keyset por {@code participante_id}, sin {@code OFFSET}: lo que se expira deja de ser {@code PENDIENTE} y saldria
 * de la cuenta, asi que un {@code OFFSET} se saltearia gente. La primera pagina arranca despues del UUID nulo, el mas
 * chico posible, para no tener un parametro opcional.
 */
@Component
class PendientesVencidosJdbcAdapter implements ConsultarPendientesVencidosPort {

    private static final UUID ANTES_DEL_PRIMERO = new UUID(0L, 0L);

    private static final String SQL = """
            SELECT participante_id, MIN(fecha_ejecucion) AS mas_vieja
            FROM renaser.registros_habito
            WHERE estado = 'PENDIENTE'
              AND fecha_ejecucion < :tope
              AND participante_id > :despuesDe
            GROUP BY participante_id
            ORDER BY participante_id
            LIMIT :limite
            """;

    private final JdbcClient jdbcClient;

    PendientesVencidosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<PendientesDeParticipante> pagina(LocalDate tope, UserId despuesDe, int limite) {
        return jdbcClient.sql(SQL)
                .param("tope", tope)
                .param("despuesDe", despuesDe == null ? ANTES_DEL_PRIMERO : despuesDe.value())
                .param("limite", limite)
                .query((rs, fila) -> new PendientesDeParticipante(
                        UserId.of(rs.getObject("participante_id", UUID.class)),
                        rs.getObject("mas_vieja", LocalDate.class)))
                .list();
    }
}
