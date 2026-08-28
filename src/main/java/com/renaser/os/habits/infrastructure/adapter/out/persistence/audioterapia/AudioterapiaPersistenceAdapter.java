package com.renaser.os.habits.infrastructure.adapter.out.persistence.audioterapia;

import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort;
import com.renaser.os.habits.application.ports.out.audioterapia.SaveAudioterapiaPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
class AudioterapiaPersistenceAdapter implements AudioterapiaCatalogPort, SaveAudioterapiaPort {

    private final SpringDataAudioterapiaRepository repository;

    AudioterapiaPersistenceAdapter(SpringDataAudioterapiaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Audioterapia> porSemana(int semana) {
        return repository.findById(semana).map(AudioterapiaPersistenceAdapter::toDomain);
    }

    @Override
    public List<Audioterapia> todasOrdenadas() {
        return repository.findAllByOrderBySemanaAsc().stream().map(AudioterapiaPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Audioterapia actualizarDuracion(int semana, int duracionDias) {
        AudioterapiaJpaEntity entidad = repository.findById(semana)
                .orElseThrow(() -> new NoSuchElementException("No existe audioterapia para la semana " + semana));
        entidad.setDuracionDias((short) duracionDias);
        return toDomain(repository.saveAndFlush(entidad));
    }

    private static Audioterapia toDomain(AudioterapiaJpaEntity e) {
        return new Audioterapia(e.getSemana(), e.getTitulo(), e.getRutaStorage(), e.getMime(), e.getTamanoBytes(),
                e.getDuracionDias());
    }
}
