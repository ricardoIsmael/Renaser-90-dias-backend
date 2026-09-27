package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.application.ports.out.celula.FotoDelGrupoPort;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code celulas.foto_ruta} y {@code foto_cambiada_en} con {@link JdbcClient} (V75, D-212): las columnas no
 * están mapeadas en {@code CelulaJpaEntity}, así que guardar un grupo por JPA no las pisa (mismo criterio
 * que {@code MarcaDeBienvenidaEnGrupoJdbcAdapter}).
 *
 * <p>Reemplazar y quitar devuelven la ruta anterior en la MISMA sentencia: la subconsulta toma la fila
 * con {@code FOR UPDATE}, así que si dos personas cambian la foto a la vez la segunda espera y recibe la
 * ruta que dejó la primera, y cada una borra exactamente el objeto que reemplazó.
 */
@Component
class FotoDelGrupoJdbcAdapter implements FotoDelGrupoPort {

    private final JdbcClient jdbcClient;

    FotoDelGrupoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<FotoDelGrupo> deGrupo(CelulaId grupo) {
        return jdbcClient.sql("SELECT foto_ruta, foto_cambiada_en FROM renaser.celulas "
                        + "WHERE id = :id AND foto_ruta IS NOT NULL")
                .param("id", grupo.value())
                .query((fila, n) -> new FotoDelGrupo(fila.getString("foto_ruta"),
                        fila.getTimestamp("foto_cambiada_en").toInstant()))
                .optional();
    }

    @Override
    public Map<CelulaId, FotoDelGrupo> deGrupos(Collection<CelulaId> grupos) {
        if (grupos.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql("SELECT id, foto_ruta, foto_cambiada_en FROM renaser.celulas "
                        + "WHERE id IN (:ids) AND foto_ruta IS NOT NULL")
                .param("ids", grupos.stream().map(CelulaId::value).distinct().toList())
                .query((fila, n) -> Map.entry(CelulaId.of(fila.getObject("id", UUID.class)), new FotoDelGrupo(
                        fila.getString("foto_ruta"), fila.getTimestamp("foto_cambiada_en").toInstant())))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Optional<String> reemplazar(CelulaId grupo, FotoDelGrupo nueva) {
        return anterior(jdbcClient.sql("""
                        UPDATE renaser.celulas c
                           SET foto_ruta = :ruta, foto_cambiada_en = :en
                          FROM (SELECT id, foto_ruta AS anterior FROM renaser.celulas WHERE id = :id FOR UPDATE) v
                         WHERE c.id = v.id
                        RETURNING v.anterior
                        """)
                .param("ruta", nueva.ruta())
                .param("en", Timestamp.from(nueva.cambiadaEn()))
                .param("id", grupo.value())
                .query(String.class)
                .list());
    }

    @Override
    public Optional<String> quitar(CelulaId grupo) {
        return anterior(jdbcClient.sql("""
                        UPDATE renaser.celulas c
                           SET foto_ruta = NULL, foto_cambiada_en = NULL
                          FROM (SELECT id, foto_ruta AS anterior FROM renaser.celulas WHERE id = :id FOR UPDATE) v
                         WHERE c.id = v.id
                        RETURNING v.anterior
                        """)
                .param("id", grupo.value())
                .query(String.class)
                .list());
    }

    /** La única fila que devuelve el UPDATE; sin fila (no existe el grupo) o sin foto previa, vacío. */
    private static Optional<String> anterior(List<String> filas) {
        return filas.stream().filter(Objects::nonNull).findFirst();
    }
}
