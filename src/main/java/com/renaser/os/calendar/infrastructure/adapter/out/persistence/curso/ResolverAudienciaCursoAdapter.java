package com.renaser.os.calendar.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.api.AccesoCursoFinder;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Puente hacia {@code academy.api.AccesoCursoFinder} (publicado por `academy` para este
 * uso exacto — CLAUDE.MD, encargo de este modulo). El servicio de `calendar` depende de
 * {@link ResolverAudienciaCursoPort}, nunca de este adaptador ni de `academy.api`
 * directamente, para poder mockear el puerto en tests unitarios sin levantar `academy`. */
@Component
class ResolverAudienciaCursoAdapter implements ResolverAudienciaCursoPort {

    private final AccesoCursoFinder accesoCursoFinder;

    ResolverAudienciaCursoAdapter(AccesoCursoFinder accesoCursoFinder) {
        this.accesoCursoFinder = accesoCursoFinder;
    }

    @Override
    public boolean tieneAcceso(UserId usuarioId, String cursoId) {
        return accesoCursoFinder.tieneAcceso(usuarioId, cursoId);
    }

    @Override
    public Set<UserId> filtrarConAcceso(String cursoId, Set<UserId> candidatos) {
        Set<UserId> conAcceso = accesoCursoFinder.usuariosConAcceso(cursoId);
        return candidatos.stream().filter(conAcceso::contains).collect(java.util.stream.Collectors.toSet());
    }
}
