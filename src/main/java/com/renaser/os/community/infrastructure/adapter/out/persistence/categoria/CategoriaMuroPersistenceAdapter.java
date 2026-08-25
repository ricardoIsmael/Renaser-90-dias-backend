package com.renaser.os.community.infrastructure.adapter.out.persistence.categoria;

import com.renaser.os.community.application.ports.out.categoria.EliminarCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.LoadCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.SaveCategoriaMuroPort;
import com.renaser.os.community.domain.model.categoria.CategoriaMuro;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class CategoriaMuroPersistenceAdapter implements LoadCategoriaMuroPort, SaveCategoriaMuroPort,
        EliminarCategoriaMuroPort {

    private final SpringDataCategoriaMuroRepository repository;
    private final CategoriaMuroPersistenceMapper mapper;
    private final EntityManager entityManager;

    CategoriaMuroPersistenceAdapter(SpringDataCategoriaMuroRepository repository,
                                     CategoriaMuroPersistenceMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<CategoriaMuro> porClave(String clave) {
        return repository.findById(clave).map(mapper::toDomain);
    }

    @Override
    public List<CategoriaMuro> listarActivas() {
        return repository.findByActivaTrueOrderByOrdenAscClaveAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CategoriaMuro> listarTodas() {
        return repository.findAllByOrderByOrdenAscClaveAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Set<String> listarClaves() {
        return repository.findAll().stream().map(CategoriaMuroJpaEntity::getClave).collect(Collectors.toSet());
    }

    @Override
    public int contarPublicaciones(String clave) {
        Number total = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM renaser.publicaciones_muro WHERE categoria_clave = ?1")
                .setParameter(1, clave)
                .getSingleResult();
        return total.intValue();
    }

    @Override
    public CategoriaMuro save(CategoriaMuro categoria) {
        var guardada = repository.saveAndFlush(mapper.toEntity(categoria));
        return mapper.toDomain(guardada);
    }

    @Override
    public void reordenar(List<String> clavesEnOrden) {
        for (int i = 0; i < clavesEnOrden.size(); i++) {
            entityManager.createNativeQuery("UPDATE renaser.categorias_muro SET orden = ?1 WHERE clave = ?2")
                    .setParameter(1, i + 1)
                    .setParameter(2, clavesEnOrden.get(i))
                    .executeUpdate();
        }
    }

    @Override
    public void eliminar(String clave) {
        repository.deleteById(clave);
    }
}
