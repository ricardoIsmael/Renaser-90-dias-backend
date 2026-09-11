package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SavePoliticaMentoriaPort;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class PoliticaMentoriaPersistenceAdapter implements LoadPoliticaMentoriaPort, SavePoliticaMentoriaPort {

    private final SpringDataPoliticaMentoriaRepository repository;
    private final Clock clock;

    PoliticaMentoriaPersistenceAdapter(SpringDataPoliticaMentoriaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<PoliticaMentoria> porCohorte(CohorteId cohorteId) {
        return repository.findById(cohorteId.value()).map(this::toDomain);
    }

    @Override
    public PoliticaMentoria save(PoliticaMentoria politica) {
        return toDomain(repository.saveAndFlush(toEntity(politica)));
    }

    private PoliticaMentoria toDomain(PoliticaMentoriaJpaEntity entidad) {
        return PoliticaMentoria.rehydrate(
                CohorteId.of(entidad.getCohorteId()),
                entidad.getCapacidadCelula(),
                entidad.getCadenciaRotacion(),
                entidad.getZonaHoraria(),
                entidad.getDiaTraslado(),
                entidad.getDiasSinActividadAlerta(),
                entidad.getCelulaRecepcionId() == null ? null : CelulaId.of(entidad.getCelulaRecepcionId()),
                entidad.getVersion());
    }

    private PoliticaMentoriaJpaEntity toEntity(PoliticaMentoria politica) {
        return new PoliticaMentoriaJpaEntity(
                politica.cohorteId().value(),
                (short) politica.capacidadCelula(),
                politica.cadenciaRotacion(),
                politica.zonaHoraria(),
                (short) politica.diaTraslado(),
                (short) politica.diasSinActividadAlerta(),
                politica.celulaRecepcionId() == null ? null : politica.celulaRecepcionId().value(),
                politica.version(),
                // creado_en lo pone la base (insertable = false); actualizado_en, este reloj.
                null,
                clock.now());
    }
}
