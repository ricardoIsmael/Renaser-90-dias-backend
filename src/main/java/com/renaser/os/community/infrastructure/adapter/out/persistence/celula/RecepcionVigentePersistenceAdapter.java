package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.application.ports.out.celula.ConsultarRecepcionVigentePort;
import com.renaser.os.community.domain.model.celula.CelulaId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Se queda con la primera: la consulta ya las ordena por el inicio mas reciente. */
@Component
class RecepcionVigentePersistenceAdapter implements ConsultarRecepcionVigentePort {

    private final SpringDataCelulaRepository repository;

    RecepcionVigentePersistenceAdapter(SpringDataCelulaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CelulaId> recepcionVigenteEn(LocalDate dia) {
        List<CelulaJpaEntity> vigentes = repository.recepcionesVigentesEn(dia);
        return vigentes.isEmpty() ? Optional.empty() : Optional.of(CelulaId.of(vigentes.getFirst().getId()));
    }
}
