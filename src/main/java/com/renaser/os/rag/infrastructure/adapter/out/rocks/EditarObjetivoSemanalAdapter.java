package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.CambioDelObjetivo;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.ResultadoEdicion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link EditarObjetivoSemanalPort} delegando en {@code rocks.api.EdicionDeObjetivoSemanalPort}
 * (D-41, D-177). Es una traduccion y nada mas.
 */
@Component
class EditarObjetivoSemanalAdapter implements EditarObjetivoSemanalPort {

    private static final VentanaDeEdicion VENTANA = new VentanaDeEdicion(
            EdicionDeObjetivoSemanalPort.VENTANA_ABRE_DOMINGO_HORA, EdicionDeObjetivoSemanalPort.VENTANA_CIERRA_LUNES_HORA,
            EdicionDeObjetivoSemanalPort.MARGEN_TARDIO_HORAS);

    private final EdicionDeObjetivoSemanalPort edicion;

    EditarObjetivoSemanalAdapter(EdicionDeObjetivoSemanalPort edicion) {
        this.edicion = edicion;
    }

    @Override
    public VentanaDeEdicion ventanaDeEdicion() {
        return VENTANA;
    }

    @Override
    public Resultado editar(UserId aprendizId, int numeroSemana, String eje, Cambio cambio) {
        CambioDelObjetivo deRocks = new CambioDelObjetivo(cambio.titulo(), cambio.obstaculo(), cambio.contingencia(),
                cambio.autoevaluacionInicio());
        return switch (edicion.editar(aprendizId, numeroSemana, eje, deRocks)) {
            case ResultadoEdicion.Editado editado -> new Resultado.Editado(editado.eje());
            case ResultadoEdicion.Rechazado rechazado -> new Resultado.Rechazado(Motivo.valueOf(rechazado.motivo().name()));
        };
    }
}
