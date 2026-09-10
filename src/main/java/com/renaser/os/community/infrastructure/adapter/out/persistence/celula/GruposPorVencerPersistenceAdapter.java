package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Traduce la proyeccion de Spring Data al record del puerto. */
@Component
class GruposPorVencerPersistenceAdapter implements ConsultarGruposPorVencerPort {

    private final SpringDataCelulaRepository repository;

    GruposPorVencerPersistenceAdapter(SpringDataCelulaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<GrupoQueVence> conCierreEntre(LocalDate desde, LocalDate hasta) {
        return repository.conCierreEntre(desde, hasta).stream()
                .map(fila -> new GrupoQueVence(fila.getCelulaId(), fila.getNombre(),
                        fila.getInicioDelPeriodo(), fila.getFinDelPeriodo()))
                .toList();
    }
}
