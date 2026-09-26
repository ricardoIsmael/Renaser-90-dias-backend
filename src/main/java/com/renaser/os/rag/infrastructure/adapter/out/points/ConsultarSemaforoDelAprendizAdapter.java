package com.renaser.os.rag.infrastructure.adapter.out.points;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implementa {@link ConsultarSemaforoDelAprendizPort} delegando en {@code points.api.SemaforoFinder}
 * (D-41, D-177): lee lo que el barrido ya guardo, sin recalcular. Pide una sola semana cerrada, la
 * ultima.
 */
@Component
class ConsultarSemaforoDelAprendizAdapter implements ConsultarSemaforoDelAprendizPort {

    private static final int SOLO_LA_ULTIMA_SEMANA = 1;

    private final SemaforoFinder semaforo;

    ConsultarSemaforoDelAprendizAdapter(SemaforoFinder semaforo) {
        this.semaforo = semaforo;
    }

    @Override
    public Optional<SemaforoDelAprendiz> de(UserId participanteId) {
        DetalleDelSemaforo detalle = semaforo.detalleDe(participanteId, SOLO_LA_ULTIMA_SEMANA);
        if (!detalle.aplica() || detalle.vigente() == null) {
            return Optional.empty();
        }
        return Optional.of(new SemaforoDelAprendiz(aTramo(detalle.vigente()), ultimaCerrada(detalle.semanas())));
    }

    private static Tramo aTramo(VentanaDelSemaforo ventana) {
        return new Tramo(ventana.desde(), ventana.hasta(), ventana.porcentaje(), ventana.color().etiqueta(),
                ventana.diasConDatos());
    }

    private static Tramo ultimaCerrada(List<SemanaCerrada> semanas) {
        if (semanas.isEmpty()) {
            return null;
        }
        SemanaCerrada ultima = semanas.getLast();
        String etiqueta = ultima.color() == null ? ColorSemaforo.SIN_DATOS.etiqueta() : ultima.color().etiqueta();
        return new Tramo(ultima.desde(), ultima.hasta(), ultima.porcentaje(), etiqueta, ultima.diasConDatos());
    }
}
