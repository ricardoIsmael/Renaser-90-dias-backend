package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.community.application.ports.out.publicacion.LoadComentarioPort;
import com.renaser.os.community.application.ports.out.publicacion.SaveComentarioPort;
import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class ComentarioPersistenceAdapter implements LoadComentarioPort, SaveComentarioPort {

    private final SpringDataComentarioRepository repository;
    private final ComentarioPersistenceMapper mapper;

    ComentarioPersistenceAdapter(SpringDataComentarioRepository repository, ComentarioPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Comentario> porId(ComentarioId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Comentario> pagina(PublicacionId publicacionId, Instant cursor, int limite) {
        var pageable = PageRequest.of(0, limite + 1);
        var filas = cursor == null
                ? repository.paginaSinCursor(publicacionId.value(), pageable)
                : repository.paginaConCursor(publicacionId.value(), cursor, pageable);
        return filas.stream().map(mapper::toDomain).toList();
    }

    @Override
    public int contar(PublicacionId publicacionId) {
        return (int) repository.countByPublicacionIdAndOcultoFalse(publicacionId.value());
    }

    @Override
    public Map<PublicacionId, Integer> contarDeVarias(Collection<PublicacionId> publicacionIds) {
        if (publicacionIds.isEmpty()) {
            return Map.of(); // un `IN ()` vacio no es SQL valido; ademas evita el viaje a la base
        }
        List<UUID> valores = publicacionIds.stream().map(PublicacionId::value).toList();
        Map<PublicacionId, Integer> resultado = new HashMap<>();
        for (Object[] fila : repository.contarPorPublicacion(valores)) {
            resultado.put(PublicacionId.of((UUID) fila[0]), ((Number) fila[1]).intValue());
        }
        return resultado;
    }

    @Override
    public Comentario save(Comentario comentario) {
        var guardado = repository.saveAndFlush(mapper.toEntity(comentario));
        return mapper.toDomain(guardado);
    }
}
