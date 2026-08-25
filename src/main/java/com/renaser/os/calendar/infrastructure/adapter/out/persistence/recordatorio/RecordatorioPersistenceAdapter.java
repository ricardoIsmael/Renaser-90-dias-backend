package com.renaser.os.calendar.infrastructure.adapter.out.persistence.recordatorio;

import com.renaser.os.calendar.application.ports.out.recordatorio.LoadRecordatorioPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
class RecordatorioPersistenceAdapter implements LoadRecordatorioPort, SaveRecordatorioPort {

    private static final String SQL_ENCOLAR = """
            INSERT INTO renaser.recordatorios_evento (evento_id, inicio_ocurrencia, usuario_id, enviar_en, creado_en)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (evento_id, inicio_ocurrencia, usuario_id, enviar_en) DO NOTHING
            """;

    private final SpringDataRecordatorioRepository repository;
    private final JdbcTemplate jdbcTemplate;

    RecordatorioPersistenceAdapter(SpringDataRecordatorioRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecordatorioEvento> vencidosPendientes(Instant hasta, int limite) {
        return repository.vencidosPendientes(hasta, PageRequest.of(0, limite)).stream()
                .map(RecordatorioPersistenceAdapter::toDomain)
                .toList();
    }

    /** Batch de INSERT ... ON CONFLICT DO NOTHING: el driver de Postgres (pgjdbc) devuelve
     * en cada posicion del batch la cantidad de filas afectadas (0 = ya existia, 1 = se
     * inserto) — se suma para saber cuantas se crearon de verdad, igual que
     * `res.count`/`skipDuplicates` del repo viejo (Prisma). */
    @Override
    public int encolarSiFalta(List<RecordatorioEvento> recordatorios) {
        if (recordatorios.isEmpty()) {
            return 0;
        }
        // batchUpdate(sql, Collection, batchSize, setter) devuelve int[][]: una fila por lote.
        int[][] resultados = jdbcTemplate.batchUpdate(SQL_ENCOLAR, recordatorios, recordatorios.size(),
                (ps, r) -> {
                    ps.setObject(1, r.eventoId().value());
                    ps.setTimestamp(2, Timestamp.from(r.inicioOcurrencia()));
                    ps.setObject(3, r.usuarioId().value());
                    ps.setTimestamp(4, Timestamp.from(r.enviarEn()));
                    ps.setTimestamp(5, Timestamp.from(r.creadoEn()));
                });
        int creados = 0;
        for (int[] lote : resultados) {
            for (int filas : lote) {
                if (filas > 0) {
                    creados += filas;
                }
            }
        }
        return creados;
    }

    @Override
    public void marcarEnviados(List<Long> ids, Instant enviadoEn) {
        if (ids.isEmpty()) {
            return;
        }
        repository.marcarEnviados(ids, enviadoEn);
    }

    @Override
    public int cancelarPorIds(List<Long> ids, String motivo) {
        return ids.isEmpty() ? 0 : repository.cancelarPorIds(ids, motivo);
    }

    @Override
    public int cancelarPorAsistencia(UserId usuarioId, EventoId eventoId, Instant inicioOcurrencia, String motivo) {
        return repository.cancelarPorAsistencia(usuarioId.value(), eventoId.value(), inicioOcurrencia, motivo);
    }

    @Override
    public int cancelarPorOcurrencia(EventoId eventoId, Instant inicioOcurrencia, String motivo) {
        return repository.cancelarPorOcurrencia(eventoId.value(), inicioOcurrencia, motivo);
    }

    @Override
    public int borrarPendientesFuturos(EventoId eventoId, Instant ahora) {
        return repository.borrarPendientesFuturos(eventoId.value(), ahora);
    }

    private static RecordatorioEvento toDomain(RecordatorioEventoJpaEntity e) {
        return RecordatorioEvento.rehydrate(e.getId(), EventoId.of(e.getEventoId()), e.getInicioOcurrencia(),
                UserId.of(e.getUsuarioId()), e.getEnviarEn(), e.getEnviadoEn(), e.getMotivoCancelacion(),
                e.getCreadoEn());
    }
}
