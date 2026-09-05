package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.application.ports.out.publicacion.EliminarPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.SavePublicacionPort;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * `medias_publicacion` se gestiona con SQL nativo dentro de este adaptador — no es un
 * `@Entity` propio (mismo criterio de `rocks/evidencia`, ver `PublicacionPersistenceMapper`).
 * Reemplazo transaccional completo al editar (borrar-todo + insertar-todo), igual que
 * `updatePost` en wall/repository.ts:117-141: un fallo a mitad no puede dejar el post sin
 * ninguna foto.
 */
@Component
class PublicacionPersistenceAdapter implements LoadPublicacionPort, SavePublicacionPort, EliminarPublicacionPort {

    private static final String SELECT_MEDIA =
            "SELECT bucket, ruta_storage, mime, orden FROM renaser.medias_publicacion "
                    + "WHERE publicacion_id = ?1 ORDER BY orden ASC";
    private static final String SELECT_MEDIA_VARIAS =
            "SELECT publicacion_id, bucket, ruta_storage, mime, orden FROM renaser.medias_publicacion "
                    + "WHERE publicacion_id IN (?1) ORDER BY orden ASC";
    private static final String DELETE_MEDIA = "DELETE FROM renaser.medias_publicacion WHERE publicacion_id = ?1";
    private static final String INSERT_MEDIA =
            "INSERT INTO renaser.medias_publicacion (id, publicacion_id, bucket, ruta_storage, mime, orden) "
                    + "VALUES (?1, ?2, ?3, ?4, ?5, ?6)";

    private final SpringDataPublicacionRepository repository;
    private final PublicacionPersistenceMapper mapper;
    private final EntityManager entityManager;

    PublicacionPersistenceAdapter(SpringDataPublicacionRepository repository, PublicacionPersistenceMapper mapper,
                                   EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Publicacion> porId(PublicacionId id) {
        return repository.findById(id.value()).map(e -> mapper.toDomain(e, mediaDe(id.value())));
    }

    @Override
    public List<Publicacion> feed(Instant cursor, int limite, String categoriaClave) {
        Pageable pageable = paginaDe(limite);
        List<PublicacionJpaEntity> filas;
        if (cursor == null && categoriaClave == null) {
            filas = repository.feedSinCursorSinCategoria(pageable);
        } else if (cursor == null) {
            filas = repository.feedSinCursorConCategoria(categoriaClave, pageable);
        } else if (categoriaClave == null) {
            filas = repository.feedConCursorSinCategoria(cursor, pageable);
        } else {
            filas = repository.feedConCursorConCategoria(cursor, categoriaClave, pageable);
        }
        return hidratarConMedia(filas);
    }

    @Override
    public List<Publicacion> feedOculto(Instant cursor, int limite) {
        Pageable pageable = paginaDe(limite);
        List<PublicacionJpaEntity> filas = cursor == null
                ? repository.feedOcultoSinCursor(pageable)
                : repository.feedOcultoConCursor(cursor, pageable);
        return hidratarConMedia(filas);
    }

    @Override
    public int contarPorAutor(UserId autorId) {
        return (int) repository.countByAutorId(autorId.value());
    }

    @Override
    public boolean existeDeAutorEntre(UserId autorId, Instant desde, Instant hasta) {
        return repository.existsByAutorIdAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(autorId.value(), desde, hasta);
    }

    @Override
    public Optional<Publicacion> ultimaVisible() {
        return repository.findFirstByOcultaFalseOrderByCreadoEnDesc()
                .map(e -> mapper.toDomain(e, mediaDe(e.getId())));
    }

    @Override
    @Transactional
    public Publicacion save(Publicacion publicacion) {
        var guardada = repository.saveAndFlush(mapper.toEntity(publicacion));
        reemplazarMedia(publicacion.id().value(), publicacion.media());
        return mapper.toDomain(guardada, publicacion.media());
    }

    @Override
    public void eliminar(PublicacionId id) {
        repository.deleteById(id.value());
    }

    private Pageable paginaDe(int limite) {
        return PageRequest.of(0, limite + 1);
    }

    private List<Publicacion> hidratarConMedia(List<PublicacionJpaEntity> filas) {
        if (filas.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = filas.stream().map(PublicacionJpaEntity::getId).toList();
        Map<UUID, List<MediaPublicacion>> mediaPorPublicacion = mediaDeVarias(ids);
        return filas.stream()
                .map(e -> mapper.toDomain(e, mediaPorPublicacion.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<MediaPublicacion> mediaDe(UUID publicacionId) {
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_MEDIA).setParameter(1, publicacionId)
                .getResultList();
        return filas.stream().map(this::aMedia).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<MediaPublicacion>> mediaDeVarias(List<UUID> publicacionIds) {
        List<Object[]> filas = entityManager.createNativeQuery(SELECT_MEDIA_VARIAS).setParameter(1, publicacionIds)
                .getResultList();
        Map<UUID, List<MediaPublicacion>> resultado = new LinkedHashMap<>();
        for (Object[] fila : filas) {
            UUID publicacionId = (UUID) fila[0];
            MediaPublicacion media = new MediaPublicacion((String) fila[1], (String) fila[2], (String) fila[3],
                    ((Number) fila[4]).intValue());
            resultado.computeIfAbsent(publicacionId, k -> new ArrayList<>()).add(media);
        }
        return resultado;
    }

    private MediaPublicacion aMedia(Object[] fila) {
        return new MediaPublicacion((String) fila[0], (String) fila[1], (String) fila[2],
                ((Number) fila[3]).intValue());
    }

    private void reemplazarMedia(UUID publicacionId, List<MediaPublicacion> media) {
        entityManager.createNativeQuery(DELETE_MEDIA).setParameter(1, publicacionId).executeUpdate();
        for (MediaPublicacion m : media) {
            entityManager.createNativeQuery(INSERT_MEDIA)
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, publicacionId)
                    .setParameter(3, m.bucket())
                    .setParameter(4, m.ruta())
                    .setParameter(5, m.mime())
                    .setParameter(6, m.orden())
                    .executeUpdate();
        }
    }
}
