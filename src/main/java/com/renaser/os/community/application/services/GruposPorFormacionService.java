package com.renaser.os.community.application.services;

import com.renaser.os.community.api.GruposPorFormacionFinder;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.stereotype.Service;

import java.util.List;

/** Las cifras de grupos por formación (spec 022). Los grupos son pocos (decenas): se cuentan en memoria sobre lo que ya lee el panel. */
@Service
class GruposPorFormacionService implements GruposPorFormacionFinder {

    private final LoadCelulaPort celulas;

    GruposPorFormacionService(LoadCelulaPort celulas) {
        this.celulas = celulas;
    }

    @Override
    public ResumenDeGrupos de(Formacion formacion) {
        List<Celula> delaFormacion = celulas.todas().stream().filter(c -> c.formacion() == formacion).toList();
        List<Celula> regulares = delaFormacion.stream().filter(c -> !c.esRecepcion()).toList();
        int conMentor = (int) regulares.stream().filter(c -> c.mentorId() != null).count();
        return new ResumenDeGrupos(delaFormacion.size(), conMentor, regulares.size() - conMentor,
                delaFormacion.size() - regulares.size());
    }
}
