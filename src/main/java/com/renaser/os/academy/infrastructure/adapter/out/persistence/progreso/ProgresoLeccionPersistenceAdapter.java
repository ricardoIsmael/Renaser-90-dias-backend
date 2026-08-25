package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import com.renaser.os.academy.application.ports.out.progreso.LoadProgresoLeccionPort;
import com.renaser.os.academy.application.ports.out.progreso.SaveProgresoLeccionPort;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class ProgresoLeccionPersistenceAdapter implements LoadProgresoLeccionPort, SaveProgresoLeccionPort {

    /** Query nativa (no JPQL cross-entidad) a proposito: mantiene este adaptador sin depender de
     * las entidades JPA de `curso`, aunque ambas vivan en el mismo modulo. */
    private static final String QUERY_COMPLETADAS_POR_CURSO = """
            SELECT l.curso_id, COUNT(*)
            FROM renaser.progreso_lecciones p
            JOIN renaser.lecciones l ON l.id = p.leccion_id
            WHERE p.usuario_id = ?1
            GROUP BY l.curso_id
            """;

    /** Espejo EN LOTE de {@link #QUERY_COMPLETADAS_POR_CURSO} — una sola consulta con
     * `IN (?1)` para todos los usuarios pedidos, en vez de una por cabeza (D-43). */
    private static final String QUERY_COMPLETADAS_POR_CURSO_EN_LOTE = """
            SELECT p.usuario_id, l.curso_id, COUNT(*)
            FROM renaser.progreso_lecciones p
            JOIN renaser.lecciones l ON l.id = p.leccion_id
            WHERE p.usuario_id IN (?1)
            GROUP BY p.usuario_id, l.curso_id
            """;

    private final SpringDataProgresoLeccionRepository repository;
    private final EntityManager entityManager;
    private final ProgresoLeccionPersistenceMapper mapper;

    ProgresoLeccionPersistenceAdapter(SpringDataProgresoLeccionRepository repository, EntityManager entityManager,
                                       ProgresoLeccionPersistenceMapper mapper) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public Set<LeccionId> leccionesCompletadas(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream()
                .map(e -> LeccionId.of(e.getLeccionId()))
                .collect(Collectors.toSet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<CursoId, Integer> completadasPorCurso(UserId usuarioId) {
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_COMPLETADAS_POR_CURSO)
                .setParameter(1, usuarioId.value())
                .getResultList();
        Map<CursoId, Integer> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.put(CursoId.of((String) fila[0]), ((Number) fila[1]).intValue());
        }
        return resultado;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UserId, Map<CursoId, Integer>> completadasPorCursoEnLote(Collection<UserId> usuarios) {
        if (usuarios.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = usuarios.stream().map(UserId::value).toList();
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_COMPLETADAS_POR_CURSO_EN_LOTE)
                .setParameter(1, ids)
                .getResultList();
        Map<UserId, Map<CursoId, Integer>> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            UserId usuarioId = UserId.of((UUID) fila[0]);
            CursoId cursoId = CursoId.of((String) fila[1]);
            int cantidad = ((Number) fila[2]).intValue();
            resultado.computeIfAbsent(usuarioId, k -> new HashMap<>()).put(cursoId, cantidad);
        }
        return resultado;
    }

    @Override
    public boolean estaCompletada(UserId usuarioId, LeccionId leccionId) {
        return repository.existsById(new ProgresoLeccionId(usuarioId.value(), leccionId.value()));
    }

    /** Idempotente: si ya existe, devuelve la fila original sin pisar `completadaEn` (mismo criterio que `markLeccionCompleted`). */
    @Override
    public ProgresoLeccion marcarCompletada(ProgresoLeccion progreso) {
        ProgresoLeccionId id = new ProgresoLeccionId(progreso.usuarioId().value(), progreso.leccionId().value());
        return repository.findById(id)
                .map(mapper::toDomain)
                .orElseGet(() -> mapper.toDomain(repository.save(mapper.toEntity(progreso))));
    }

    /**
     * Idempotente a proposito (AC-16): `deleteById` de Spring Data lanza
     * {@code EmptyResultDataAccessException} si la fila no existe, y el DELETE
     * de PostgREST que este metodo reemplaza (`.delete().eq(...)`) no falla
     * borrando cero filas — se guarda con `existsById` antes para igualar ese
     * comportamiento.
     */
    @Override
    public void desmarcarCompletada(UserId usuarioId, LeccionId leccionId) {
        ProgresoLeccionId id = new ProgresoLeccionId(usuarioId.value(), leccionId.value());
        if (repository.existsById(id)) {
            repository.deleteById(id);
        }
    }
}
