package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Solo lectura (toDomain) — este modulo no construyo casos de uso de alta de
 * catalogo (fuera de alcance, ver `docs/MODULO_ACADEMY.md` §6), asi que no
 * hace falta `toEntity`.
 */
@Component
class CursoPersistenceMapper {

    Curso toDomain(CursoJpaEntity e, Set<UserRole> rolesPermitidos) {
        return new Curso(CursoId.of(e.getId()), e.getSlug(), e.getTitulo(), e.getDescripcion(), e.getPortadaRuta(),
                e.getOrden(), e.isPublicado(), toDomainAcceso(e.getAcceso()), e.getOrigen(),
                e.getDiaDesbloqueo() == null ? null : e.getDiaDesbloqueo().intValue(), rolesPermitidos,
                e.getCreadoEn(), e.getActualizadoEn());
    }

    private AccesoCurso toDomainAcceso(AccesoCursoJpa jpa) {
        return switch (jpa) {
            case ABIERTO -> AccesoCurso.ABIERTO;
            case RESTRINGIDO -> AccesoCurso.RESTRINGIDO;
        };
    }
}
