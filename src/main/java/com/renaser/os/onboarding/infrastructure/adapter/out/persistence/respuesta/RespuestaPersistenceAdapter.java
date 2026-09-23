package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class RespuestaPersistenceAdapter implements LoadRespuestaPort, SaveRespuestaPort {

    /**
     * El upsert lo decide la base, en UNA sentencia.
     *
     * > <b>Corregido el 2026-09-23 (E-213).</b> Antes era "buscar por (usuario, pregunta) y, si no
     * > existe, insertar" en dos pasos. Dos guardados simultaneos de la misma pregunta —el Mapa
     * > guarda la prioridad por {@code guardarPrioridad} y otra vez dentro de
     * > {@code guardarRespuestasDelMapa}— buscaban a la vez, los dos veian "no existe", y el
     * > segundo chocaba contra {@code respuestas_onboarding_usuario_id_pregunta_id_key}: un 409
     * > que la persona veia en pantalla aunque su respuesta ya estaba guardada.
     *
     * <p>{@code respondida_en} no se pisa al actualizar: es cuando se contesto por primera vez.
     * Los casts explicitos son para que un parametro {@code null} tenga tipo; sin ellos Postgres
     * no sabe si es texto, numero o jsonb.
     */
    private static final String UPSERT = """
            INSERT INTO renaser.respuestas_onboarding
                (usuario_id, pregunta_id, valor_texto, valor_numero, valor_booleano, valor_escala,
                 valor_json, media_id, aceptada_en, respondida_en, actualizado_en)
            VALUES (CAST(:usuarioId AS uuid), :preguntaId, CAST(:valorTexto AS text),
                    CAST(:valorNumero AS numeric), CAST(:valorBooleano AS boolean),
                    CAST(:valorEscala AS smallint), CAST(:valorJson AS jsonb), CAST(:mediaId AS bigint),
                    CAST(:aceptadaEn AS timestamptz), CAST(:respondidaEn AS timestamptz),
                    CAST(:actualizadoEn AS timestamptz))
            ON CONFLICT (usuario_id, pregunta_id) DO UPDATE SET
                valor_texto    = EXCLUDED.valor_texto,
                valor_numero   = EXCLUDED.valor_numero,
                valor_booleano = EXCLUDED.valor_booleano,
                valor_escala   = EXCLUDED.valor_escala,
                valor_json     = EXCLUDED.valor_json,
                media_id       = EXCLUDED.media_id,
                aceptada_en    = EXCLUDED.aceptada_en,
                actualizado_en = EXCLUDED.actualizado_en
            RETURNING id, usuario_id, pregunta_id, valor_texto, valor_numero, valor_booleano,
                      valor_escala, valor_json::text AS valor_json, media_id, aceptada_en,
                      respondida_en, actualizado_en
            """;

    private final SpringDataRespuestaOnboardingRepository repository;
    private final RespuestaPersistenceMapper mapper;
    private final JdbcClient jdbcClient;

    RespuestaPersistenceAdapter(SpringDataRespuestaOnboardingRepository repository,
                                 RespuestaPersistenceMapper mapper, JdbcClient jdbcClient) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Respuesta> porUsuarioYPregunta(UserId usuarioId, int preguntaId) {
        return repository.findByUsuarioIdAndPreguntaId(usuarioId.value(), preguntaId).map(mapper::toDomain);
    }

    @Override
    public List<Respuesta> todasDeUsuario(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(mapper::toDomain).toList();
    }

    /** UPSERT atomico por {@code (usuarioId, preguntaId)}. Ver {@link #UPSERT}. */
    @Override
    public Respuesta guardar(Respuesta r) {
        return jdbcClient.sql(UPSERT)
                .param("usuarioId", r.usuarioId().value())
                .param("preguntaId", r.preguntaId())
                .param("valorTexto", r.valorTexto())
                .param("valorNumero", r.valorNumero())
                .param("valorBooleano", r.valorBooleano())
                .param("valorEscala", r.valorEscala())
                .param("valorJson", r.valorJson())
                .param("mediaId", r.mediaId())
                .param("aceptadaEn", aTimestamp(r.aceptadaEn()))
                .param("respondidaEn", aTimestamp(r.respondidaEn()))
                .param("actualizadoEn", aTimestamp(r.actualizadoEn()))
                .query(RespuestaPersistenceAdapter::aDominio)
                .single();
    }

    private static Timestamp aTimestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private static Respuesta aDominio(ResultSet rs, int fila) throws SQLException {
        return Respuesta.rehydrate(rs.getLong("id"), UserId.of(rs.getObject("usuario_id", UUID.class)),
                rs.getInt("pregunta_id"), rs.getString("valor_texto"), rs.getBigDecimal("valor_numero"),
                (Boolean) rs.getObject("valor_booleano"),
                rs.getObject("valor_escala") == null ? null : rs.getShort("valor_escala"),
                rs.getString("valor_json"), rs.getObject("media_id") == null ? null : rs.getLong("media_id"),
                aInstante(rs.getTimestamp("aceptada_en")), aInstante(rs.getTimestamp("respondida_en")),
                aInstante(rs.getTimestamp("actualizado_en")));
    }

    private static Instant aInstante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
