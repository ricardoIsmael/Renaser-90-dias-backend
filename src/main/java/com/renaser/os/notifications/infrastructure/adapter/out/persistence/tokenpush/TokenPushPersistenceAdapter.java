package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
class TokenPushPersistenceAdapter implements UpsertTokenPushPort, LoadTokenPushPort {

    /**
     * C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): el UPSERT
     * anterior era {@code findByToken} y decidir INSERT o UPDATE en Java — un check-then-act
     * clasico. Dos registros casi simultaneos del MISMO token (la app movil reintenta
     * {@code POST /push-tokens} si el primero tarda o el response se pierde) podian ver los
     * dos "no existe" y los dos intentar el INSERT; el segundo perdia contra el
     * {@code UNIQUE(token)} y {@code GlobalExceptionHandler} lo traducia a un 409 para una
     * operacion que el cliente percibe como "guardar mi token", no un conflicto real.
     *
     * <p>Ademas, {@code TokenPushService#registrar} ya corre dentro de su propia
     * {@code @Transactional}: "atrapar la violacion de unicidad y releer" DENTRO de esa misma
     * transaccion no es una opcion (Postgres la deja abortada apenas el INSERT falla —
     * cualquier sentencia posterior, incluida la relectura, explotaria con "current
     * transaction is aborted"). Por eso la solucion es que el UPSERT nunca lance por una
     * carrera: {@code INSERT ... ON CONFLICT (token) DO UPDATE} es atomico en la base — sea
     * cual sea el orden real de llegada, las dos llamadas terminan viendo la MISMA fila (mismo
     * id), sin 409 y sin duplicar. Mismo criterio que
     * {@code ReaccionMuroPersistenceAdapter}/{@code RecordatorioPersistenceAdapter}, que ya
     * resuelven el mismo problema con SQL nativo en vez de "leer y despues decidir".
     */
    private static final String UPSERT_SQL = """
            INSERT INTO renaser.tokens_push (id, usuario_id, token, plataforma, creado_en, actualizado_en)
            VALUES (?, ?, ?, CAST(? AS renaser.plataforma_push), ?, ?)
            ON CONFLICT (token) DO UPDATE SET
                usuario_id     = EXCLUDED.usuario_id,
                plataforma     = EXCLUDED.plataforma,
                actualizado_en = EXCLUDED.actualizado_en
            RETURNING id, usuario_id, token, plataforma, creado_en, actualizado_en
            """;

    private final RowMapper<TokenPushJpaEntity> rowMapper = (rs, rowNum) -> {
        TokenPushJpaEntity entidad = new TokenPushJpaEntity();
        entidad.setId(rs.getObject("id", UUID.class));
        entidad.setUsuarioId(rs.getObject("usuario_id", UUID.class));
        entidad.setToken(rs.getString("token"));
        String plataforma = rs.getString("plataforma");
        entidad.setPlataforma(plataforma == null ? null : PlataformaPushJpa.valueOf(plataforma));
        entidad.setCreadoEn(rs.getTimestamp("creado_en").toInstant());
        entidad.setActualizadoEn(rs.getTimestamp("actualizado_en").toInstant());
        return entidad;
    };

    private final SpringDataTokenPushRepository repository;
    private final TokenPushPersistenceMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    TokenPushPersistenceAdapter(SpringDataTokenPushRepository repository, TokenPushPersistenceMapper mapper,
                                 JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TokenPush upsertPorToken(TokenPush tokenPush) {
        TokenPushJpaEntity fila = jdbcTemplate.queryForObject(UPSERT_SQL, rowMapper,
                tokenPush.id().value(), tokenPush.usuarioId().value(), tokenPush.token(),
                tokenPush.plataforma() == null ? null : tokenPush.plataforma().name(),
                Timestamp.from(tokenPush.creadoEn()), Timestamp.from(tokenPush.actualizadoEn()));
        return mapper.toDomain(fila);
    }

    @Override
    public List<String> tokensDe(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(TokenPushJpaEntity::getToken).toList();
    }
}
