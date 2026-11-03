package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.SaveRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RocaSemanalPersistenceAdapter implements LoadRocaSemanalPort, SaveRocaSemanalPort {

    private final SpringDataRocaSemanalRepository repository;
    private final RocaSemanalPersistenceMapper mapper;

    RocaSemanalPersistenceAdapter(SpringDataRocaSemanalRepository repository, RocaSemanalPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RocaSemanal> byId(RocaSemanalId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<RocaSemanal> deParticipanteYSemana(List<RocaMaestraId> rocasMaestrasIds, int numeroSemana) {
        List<java.util.UUID> ids = rocasMaestrasIds.stream().map(RocaMaestraId::value).toList();
        return repository.findByRocaMaestraIdInAndNumeroSemana(ids, aCorto(numeroSemana)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<RocaSemanal> deMaestraYSemana(RocaMaestraId rocaMaestraId, int numeroSemana) {
        return repository.findByRocaMaestraIdAndNumeroSemana(rocaMaestraId.value(), aCorto(numeroSemana))
                .map(mapper::toDomain);
    }

    @Override
    public RocaSemanal save(RocaSemanal rocaSemanal) {
        var saved = repository.saveAndFlush(mapper.toEntity(rocaSemanal));
        return mapper.toDomain(saved);
    }

    /**
     * El número llega de un pedido y la columna es {@code smallint}: un valor fuera de rango se rechaza en vez de truncarse en
     * silencio al convertirlo (CodeQL java/tainted-numeric-cast). Ninguno válido pasa de unas decenas.
     */
    private static short aCorto(int numero) {
        if (numero < 0 || numero > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Número fuera de rango: " + numero);
        }
        return (short) numero;
    }
}
