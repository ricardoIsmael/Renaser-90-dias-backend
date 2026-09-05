package com.renaser.os.rag.infrastructure.adapter.out.academy;

import com.renaser.os.academy.api.LeccionesVisiblesFinder;
import com.renaser.os.rag.application.ports.out.conversacion.ConsultarLeccionesVisiblesPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Implementa {@link ConsultarLeccionesVisiblesPort} delegando en el contrato público de
 * {@code academy} (D-41, mismo patrón que {@code habits.LeerEntradasDiarioAdapter} con
 * {@code habits.api.EntradaDiarioFinder}) — {@code rag} nunca calcula por su cuenta el gate
 * de programa de {@code academy}, ni consulta sus tablas de frente. Tampoco intersecta por
 * curso en memoria (D-102): pedirle a {@code academy} "las visibles de ESTE curso" es una
 * consulta menos y deja la regla en un solo lugar.
 */
@Component
class LeerLeccionesVisiblesAdapter implements ConsultarLeccionesVisiblesPort {

    private final LeccionesVisiblesFinder leccionesVisiblesFinder;

    LeerLeccionesVisiblesAdapter(LeccionesVisiblesFinder leccionesVisiblesFinder) {
        this.leccionesVisiblesFinder = leccionesVisiblesFinder;
    }

    @Override
    public Set<String> visiblesParaActor(UserId actorId) {
        return leccionesVisiblesFinder.leccionesVisiblesPara(actorId);
    }

    @Override
    public Set<String> visiblesParaActorEnCurso(UserId actorId, String cursoId) {
        return leccionesVisiblesFinder.leccionesVisiblesPara(actorId, cursoId);
    }
}
