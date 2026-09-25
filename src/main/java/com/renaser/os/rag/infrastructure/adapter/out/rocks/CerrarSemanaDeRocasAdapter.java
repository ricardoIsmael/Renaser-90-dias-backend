package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rocks.api.CierreDeSemanaPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementa {@link CerrarSemanaDeRocasPort} delegando en {@code rocks.api.CierreDeSemanaPort} (D-41):
 * {@code rag} nunca escribe en las tablas de rocas. Es una traduccion y nada mas.
 */
@Component
class CerrarSemanaDeRocasAdapter implements CerrarSemanaDeRocasPort {

    private static final ReglasDelCierre REGLAS = new ReglasDelCierre(PlanificacionDeRocasPort.EJES,
            CierreDeSemanaPort.AUTOEVALUACION_MINIMA, CierreDeSemanaPort.AUTOEVALUACION_MAXIMA);

    private final CierreDeSemanaPort cierre;

    CerrarSemanaDeRocasAdapter(CierreDeSemanaPort cierre) {
        this.cierre = cierre;
    }

    @Override
    public ReglasDelCierre reglas() {
        return REGLAS;
    }

    @Override
    public ResultadoCierre cerrarSemana(UserId aprendizId, int numeroSemana, List<RevisionDelEje> revisiones) {
        List<CierreDeSemanaPort.RevisionDelEje> deRocks = revisiones.stream()
                .map(r -> new CierreDeSemanaPort.RevisionDelEje(r.eje(), r.autoevaluacion(), r.bloqueoPrincipal(),
                        r.correccion()))
                .toList();
        return switch (cierre.cerrarSemana(aprendizId, numeroSemana, deRocks)) {
            case CierreDeSemanaPort.ResultadoCierre.Cerrada cerrada -> new ResultadoCierre.Cerrada(cerrada.ejes());
            case CierreDeSemanaPort.ResultadoCierre.Rechazado rechazado ->
                    new ResultadoCierre.Rechazado(Motivo.valueOf(rechazado.motivo().name()));
        };
    }
}
