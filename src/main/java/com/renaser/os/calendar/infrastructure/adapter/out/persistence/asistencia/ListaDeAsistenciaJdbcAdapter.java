package com.renaser.os.calendar.infrastructure.adapter.out.persistence.asistencia;

import com.renaser.os.calendar.application.ports.out.asistencia.LoadListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.asistencia.SaveListaDeAsistenciaPort;
import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.EstadoAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code asistencias_evento} y {@code listas_asistencia_evento} (V93, D-256) con {@link JdbcClient}. Las marcas
 * se escriben con {@code ON CONFLICT}: dos toques que llegan a la vez (o un reintento de la app) dejan una
 * sola fila, la del último. Los instantes viajan como {@link OffsetDateTime} en UTC.
 */
@Component
class ListaDeAsistenciaJdbcAdapter implements LoadListaDeAsistenciaPort, SaveListaDeAsistenciaPort {

    private static final String COLUMNAS_MARCA =
            "evento_id, inicio_ocurrencia, usuario_id, estado, marcado_por, marcado_en";

    private final JdbcClient jdbcClient;

    ListaDeAsistenciaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<MarcaDeAsistencia> marcas(EventoId eventoId, Instant inicioOcurrencia) {
        return jdbcClient.sql("SELECT " + COLUMNAS_MARCA + " FROM renaser.asistencias_evento "
                        + "WHERE evento_id = :evento AND inicio_ocurrencia = :slot")
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .query(MARCA)
                .list();
    }

    @Override
    public Optional<MarcaDeAsistencia> marcaDe(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId) {
        return jdbcClient.sql("SELECT " + COLUMNAS_MARCA + " FROM renaser.asistencias_evento "
                        + "WHERE evento_id = :evento AND inicio_ocurrencia = :slot AND usuario_id = :usuario")
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .param("usuario", usuarioId.value())
                .query(MARCA)
                .optional();
    }

    @Override
    public Optional<CierreDeLista> cierre(EventoId eventoId, Instant inicioOcurrencia) {
        return jdbcClient.sql("SELECT cerrada_en, cerrada_por FROM renaser.listas_asistencia_evento "
                        + "WHERE evento_id = :evento AND inicio_ocurrencia = :slot")
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .query((rs, n) -> new CierreDeLista(rs.getObject("cerrada_en", OffsetDateTime.class).toInstant(),
                        usuario(rs.getObject("cerrada_por", UUID.class))))
                .optional();
    }

    @Override
    public void guardar(MarcaDeAsistencia marca) {
        jdbcClient.sql("""
                        INSERT INTO renaser.asistencias_evento (%s)
                        VALUES (:evento, :slot, :usuario, :estado, :por, :en)
                        ON CONFLICT (evento_id, inicio_ocurrencia, usuario_id)
                        DO UPDATE SET estado = EXCLUDED.estado, marcado_por = EXCLUDED.marcado_por,
                                      marcado_en = EXCLUDED.marcado_en
                        """.formatted(COLUMNAS_MARCA))
                .param("evento", marca.eventoId().value())
                .param("slot", utc(marca.inicioOcurrencia()))
                .param("usuario", marca.usuarioId().value())
                .param("estado", marca.estado().name())
                .param("por", marca.marcadoPor() == null ? null : marca.marcadoPor().value())
                .param("en", utc(marca.marcadoEn()))
                .update();
    }

    @Override
    public void quitar(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId) {
        jdbcClient.sql("DELETE FROM renaser.asistencias_evento "
                        + "WHERE evento_id = :evento AND inicio_ocurrencia = :slot AND usuario_id = :usuario")
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .param("usuario", usuarioId.value())
                .update();
    }

    @Override
    public void cerrar(EventoId eventoId, Instant inicioOcurrencia, CierreDeLista cierre) {
        jdbcClient.sql("""
                        INSERT INTO renaser.listas_asistencia_evento (evento_id, inicio_ocurrencia, cerrada_en, cerrada_por)
                        VALUES (:evento, :slot, :en, :por)
                        ON CONFLICT (evento_id, inicio_ocurrencia) DO NOTHING
                        """)
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .param("en", utc(cierre.cerradaEn()))
                .param("por", cierre.cerradaPor() == null ? null : cierre.cerradaPor().value())
                .update();
    }

    @Override
    public void reabrir(EventoId eventoId, Instant inicioOcurrencia) {
        jdbcClient.sql("DELETE FROM renaser.listas_asistencia_evento WHERE evento_id = :evento AND inicio_ocurrencia = :slot")
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .update();
    }

    private static final RowMapper<MarcaDeAsistencia> MARCA = (rs, n) -> new MarcaDeAsistencia(
            EventoId.of(rs.getObject("evento_id", UUID.class)),
            rs.getObject("inicio_ocurrencia", OffsetDateTime.class).toInstant(),
            UserId.of(rs.getObject("usuario_id", UUID.class)),
            EstadoAsistencia.valueOf(rs.getString("estado")),
            usuario(rs.getObject("marcado_por", UUID.class)),
            rs.getObject("marcado_en", OffsetDateTime.class).toInstant());

    private static UserId usuario(UUID id) {
        return id == null ? null : UserId.of(id);
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
