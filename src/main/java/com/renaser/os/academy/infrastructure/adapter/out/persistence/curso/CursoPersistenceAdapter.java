package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class CursoPersistenceAdapter implements LoadCursoPort {

    private final SpringDataCursoRepository cursoRepository;
    private final SpringDataRolPermitidoCursoRepository rolPermitidoRepository;
    private final RolesCatalogo rolesCatalogo;
    private final CursoPersistenceMapper mapper;

    CursoPersistenceAdapter(SpringDataCursoRepository cursoRepository,
                             SpringDataRolPermitidoCursoRepository rolPermitidoRepository,
                             RolesCatalogo rolesCatalogo, CursoPersistenceMapper mapper) {
        this.cursoRepository = cursoRepository;
        this.rolPermitidoRepository = rolPermitidoRepository;
        this.rolesCatalogo = rolesCatalogo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Curso> byId(CursoId id) {
        return cursoRepository.findById(id.value()).map(e -> mapper.toDomain(e, rolesDe(e.getId())));
    }

    @Override
    public List<Curso> listarTodos() {
        List<CursoJpaEntity> entidades = cursoRepository.findAllByOrderByOrdenAsc();
        if (entidades.isEmpty()) {
            return List.of();
        }
        List<String> ids = entidades.stream().map(CursoJpaEntity::getId).toList();
        Map<String, Set<UserRole>> rolesPorCurso = rolPermitidoRepository.findByCursoIdIn(ids).stream()
                .collect(Collectors.groupingBy(RolPermitidoCursoJpaEntity::getCursoId,
                        Collectors.mapping(r -> rolesCatalogo.claveDe(r.getRolId()), Collectors.toSet())));
        return entidades.stream()
                .map(e -> mapper.toDomain(e, rolesPorCurso.getOrDefault(e.getId(), Set.of())))
                .toList();
    }

    private Set<UserRole> rolesDe(String cursoId) {
        return rolPermitidoRepository.findByCursoId(cursoId).stream()
                .map(r -> rolesCatalogo.claveDe(r.getRolId()))
                .collect(Collectors.toSet());
    }
}
