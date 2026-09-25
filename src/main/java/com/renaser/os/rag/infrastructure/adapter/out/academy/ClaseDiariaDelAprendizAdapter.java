package com.renaser.os.rag.infrastructure.adapter.out.academy;

import com.renaser.os.academy.api.ClaseDiariaPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementa {@link ClaseDiariaDelAprendizPort} delegando en {@code academy.api.ClaseDiariaPort}
 * (D-41). Traduccion y nada mas: las excepciones del negocio pasan tal cual, para que la
 * herramienta o el confirmable las conviertan en un {@code Fallo} legible. Mismo patron que
 * {@link LeerLeccionesVisiblesAdapter}.
 */
@Component
class ClaseDiariaDelAprendizAdapter implements ClaseDiariaDelAprendizPort {

    private final ClaseDiariaPort claseDiaria;

    ClaseDiariaDelAprendizAdapter(ClaseDiariaPort claseDiaria) {
        this.claseDiaria = claseDiaria;
    }

    @Override
    public ClaseDeHoy claseDeHoy(UserId actorId) {
        ClaseDiariaPort.ClaseDeHoy clase = claseDiaria.claseDeHoy(actorId);
        return new ClaseDeHoy(aEstado(clase.estado()), clase.diaPrograma(), clase.cursoTitulo(), clase.leccionId(),
                clase.leccionTitulo(), clase.leccionVista(), ClaseDiariaPort.RESUMEN_MIN_LENGTH,
                ClaseDiariaPort.RESUMEN_MAX_LENGTH);
    }

    @Override
    public int entregar(UserId actorId, String leccionId, String resumen) {
        return claseDiaria.entregar(actorId, leccionId, resumen).puntosOtorgados();
    }

    @Override
    public Optional<RecomendacionDeHoy> recomendacionDeHoySiExiste(UserId actorId) {
        return claseDiaria.recomendacionDeHoySiExiste(actorId)
                .map(r -> new RecomendacionDeHoy(r.cursoTitulo(), r.leccionTitulo(), r.motivo()));
    }

    private static EstadoClase aEstado(ClaseDiariaPort.Estado estado) {
        return switch (estado) {
            case DISPONIBLE -> EstadoClase.DISPONIBLE;
            case NO_INICIADO -> EstadoClase.NO_INICIADO;
            case PROXIMAMENTE -> EstadoClase.PROXIMAMENTE;
        };
    }
}
