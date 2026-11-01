package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.acompanamiento.IngresarAlGrupoDeRecepcionUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.ReubicarAlTrasladarDeFormacionUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Al pasar de formación el aprendiz deja TODOS sus grupos (puede tener varios, D-139) y entra al de bienvenida de la nueva (spec 022, Q1-A).
 * Cada grupo que deja se avisa para que el chat reconcilie sus integrantes. Los intervalos se cierran con el motivo {@code TRASLADO}: queda
 * la historia de dónde estuvo.
 */
@Service
class ReubicacionPorTrasladoDeFormacionService implements ReubicarAlTrasladarDeFormacionUseCase {

    private final LoadAsignacionesPort cargar;
    private final SaveAsignacionPort guardar;
    private final IngresarAlGrupoDeRecepcionUseCase recepcion;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    ReubicacionPorTrasladoDeFormacionService(LoadAsignacionesPort cargar, SaveAsignacionPort guardar,
                                             IngresarAlGrupoDeRecepcionUseCase recepcion, ApplicationEventPublisher eventos,
                                             Clock clock) {
        this.cargar = cargar;
        this.guardar = guardar;
        this.recepcion = recepcion;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reubicar(UserId aprendiz, Formacion hacia, Instant trasladadoEn) {
        Instant ahora = clock.now();
        List<AsignacionCelula> vigentes = cargar.porUsuario(aprendiz).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(ahora))
                .toList();
        for (AsignacionCelula asignacion : vigentes) {
            asignacion.cerrar(ahora, MotivoAsignacion.TRASLADO);
            guardar.save(asignacion);
            eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(asignacion.celulaId().value(), ahora));
        }
        recepcion.ingresarPorTraslado(aprendiz, hacia, trasladadoEn);
    }
}
