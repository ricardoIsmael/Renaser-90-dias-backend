package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.application.ports.out.publicacion.ReaccionMuroPort;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
