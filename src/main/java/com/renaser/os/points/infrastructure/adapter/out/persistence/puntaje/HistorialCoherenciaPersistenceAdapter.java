package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.points.application.ports.out.puntaje.SaveHistorialCoherenciaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * `save()` sobre una entidad con PK asignada a mano hace merge (upsert), no siempre
 * insert — mismo comportamiento que UserJpaEntity/AccountRequestJpaEntity ya usan.
 */
@Component
class HistorialCoherenciaPersistenceAdapter implements SaveHistorialCoherenciaPort {

    private final SpringDataHistorialCoherenciaRepository repository;

    HistorialCoherenciaPersistenceAdapter(SpringDataHistorialCoherenciaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(UserId participanteId, LocalDate fecha, BigDecimal valor) {
        repository.saveAndFlush(new HistorialCoherenciaJpaEntity(participanteId.value(), fecha, valor));
    }
}
