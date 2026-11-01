package com.renaser.os.rocks.infrastructure.adapter.out.diagnostico;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarObjetivosDelDiagnosticoPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/** Traduce {@code onboarding.api.DiagnosticoDeLaPersonaFinder} al tipo propio de {@code rocks}: los ejes viajan por nombre. */
@Component
class ObjetivosDelDiagnosticoAdapter implements ConsultarObjetivosDelDiagnosticoPort {

    private final DiagnosticoDeLaPersonaFinder diagnostico;

    ObjetivosDelDiagnosticoAdapter(DiagnosticoDeLaPersonaFinder diagnostico) {
        this.diagnostico = diagnostico;
    }

    @Override
    public List<ObjetivoDelDiagnostico> de(UserId participanteId) {
        return diagnostico.objetivosDe(participanteId).objetivos().stream()
                .map(o -> new ObjetivoDelDiagnostico(EjeObjetivo.valueOf(o.eje()), o.texto(),
                        o.meta() == null ? null : new MetaDelDiagnostico(o.meta().meta(), o.meta().partida(), o.meta().unidad())))
                .toList();
    }
}
