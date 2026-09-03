package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.application.ports.out.publicacion.ReaccionMuroPort;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * `reacciones_muro` no tiene identidad propia (PK compuesta publicacion+usuario,
 * V1__baseline_renaser.sql:1106-1112) — se gestiona con SQL nativo, sin `@Entity`
 * separado, mismo criterio que `medias_publicacion`.
 */
@Component
class ReaccionMuroPersistenceAdapter implements ReaccionMuroPort {

    private static final String SELECT_TIPO =
            "SELECT tipo FROM renaser.reacciones_muro WHERE publicacion_id = ?1 AND usuario_id = ?2";
    private static final String SELECT_CONTEO =
            "SELECT tipo, COUNT(*) FROM renaser.reacciones_muro WHERE publicacion_id = ?1 GROUP BY tipo";
    /** Las dos consultas en lote del feed (E-80). Se apoyan en la PK compuesta
     * (publicacion_id, usuario_id), asi que el `IN` recorre indice igual que la version de a una. */
    private static final String SELECT_CONTEO_VARIAS =
            "SELECT publicacion_id, tipo, COUNT(*) FROM renaser.reacciones_muro "
                    + "WHERE publicacion_id IN (?1) GROUP BY publicacion_id, tipo";
    private static final String SELECT_TIPO_VARIAS =
            "SELECT publicacion_id, tipo FROM renaser.reacciones_muro "
                    + "WHERE publicacion_id IN (?1) AND usuario_id = ?2";
    private static final String SELECT_LISTADO =
            "SELECT usuario_id, tipo FROM renaser.reacciones_muro "
                    + "WHERE publicacion_id = ?1 ORDER BY creado_en DESC";
    private static final String UPSERT = """
            INSERT INTO renaser.reacciones_muro (publicacion_id, usuario_id, tipo, creado_en)
            VALUES (?1, ?2, CAST(?3 AS renaser.tipo_reaccion), now())
            ON CONFLICT (publicacion_id, usuario_id) DO UPDATE SET tipo = EXCLUDED.tipo
            """;
    private static final String DELETE = "DELETE FROM renaser.reacciones_muro WHERE publicacion_id = ?1 AND usuario_id = ?2";

    private final EntityManager entityManager;

    ReaccionMuroPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<TipoReaccion> deUsuario(PublicacionId publicacionId, UserId usuarioId) {
        List<String> filas = entityManager.createNativeQuery(SELECT_TIPO)
                .setParameter(1, publicacionId.value())
                .setParameter(2, usuarioId.value())
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(TipoReaccion.valueOf(filas.get(0)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<TipoReaccion, Integer> contarPorTipo(PublicacionId publicacionId) {
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_CONTEO)
                .setParameter(1, publicacionId.value())
                .getResultList();
        Map<TipoReaccion, Integer> resultado = new EnumMap<>(TipoReaccion.class);
        for (Object[] fila : filas) {
            resultado.put(TipoReaccion.valueOf((String) fila[0]), ((Number) fila[1]).intValue());
        }
        return resultado;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<PublicacionId, Map<TipoReaccion, Integer>> contarPorTipoDeVarias(
            Collection<PublicacionId> publicacionIds) {
        if (publicacionIds.isEmpty()) {
            // Postgres rechaza `IN ()` con error de sintaxis: sin este corte, un feed vacio
            // (primer dia, o una categoria sin publicaciones) reventaria en vez de dar una pagina
            // vacia. Mismo motivo en los otros dos metodos en lote.
            return Map.of();
        }
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_CONTEO_VARIAS)
                .setParameter(1, valoresDe(publicacionIds))
                .getResultList();
        Map<PublicacionId, Map<TipoReaccion, Integer>> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.computeIfAbsent(PublicacionId.of((UUID) fila[0]), k -> new EnumMap<>(TipoReaccion.class))
                    .put(TipoReaccion.valueOf((String) fila[1]), ((Number) fila[2]).intValue());
        }
        return resultado;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<PublicacionId, TipoReaccion> deUsuarioEnVarias(Collection<PublicacionId> publicacionIds,
                                                                UserId usuarioId) {
        if (publicacionIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_TIPO_VARIAS)
                .setParameter(1, valoresDe(publicacionIds))
                .setParameter(2, usuarioId.value())
                .getResultList();
        Map<PublicacionId, TipoReaccion> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.put(PublicacionId.of((UUID) fila[0]), TipoReaccion.valueOf((String) fila[1]));
        }
        return resultado;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReaccionMuro> listarDe(PublicacionId publicacionId) {
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_LISTADO)
                .setParameter(1, publicacionId.value())
                .getResultList();
        List<ReaccionMuro> resultado = new ArrayList<>(filas.size());
        for (Object[] fila : filas) {
            resultado.add(new ReaccionMuro(publicacionId, UserId.of((UUID) fila[0]),
                    TipoReaccion.valueOf((String) fila[1])));
        }
        return resultado;
    }

    private static List<UUID> valoresDe(Collection<PublicacionId> publicacionIds) {
        return publicacionIds.stream().map(PublicacionId::value).toList();
    }

    @Override
    @Transactional
    public void upsert(PublicacionId publicacionId, UserId usuarioId, TipoReaccion tipo) {
        entityManager.createNativeQuery(UPSERT)
                .setParameter(1, publicacionId.value())
                .setParameter(2, usuarioId.value())
                .setParameter(3, tipo.name())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void eliminar(PublicacionId publicacionId, UserId usuarioId) {
        entityManager.createNativeQuery(DELETE)
                .setParameter(1, publicacionId.value())
                .setParameter(2, usuarioId.value())
                .executeUpdate();
    }
}
