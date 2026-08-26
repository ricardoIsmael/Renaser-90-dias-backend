package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
class HistorialCambioHorarioPersistenceAdapter implements HistorialCambioHorarioPort {

    /** Unico valor de `accion` que escribe este flujo — distinto de lo que escriba, si algo, `preferencia/`. */
    private static final String ACCION_EDICION_PARTICIPANTE = "EDICION_PARTICIPANTE";

    private final SpringDataHistorialCambioHorarioRepository repository;

    HistorialCambioHorarioPersistenceAdapter(SpringDataHistorialCambioHorarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<HabitoId> distintosHabitosCambiadosDesde(UserId participanteId, LocalDate desde) {
        return repository.habitosDistintosDesde(participanteId.value(), desde).stream().map(HabitoId::of).toList();
    }

    @Override
    public void registrar(UserId participanteId, HabitoId habitoId, LocalDate cambiadoEl, LocalTime horaDisparo,
                           LocalTime horaLimite, Instant ahora) {
        repository.save(new HistorialCambioHorarioJpaEntity(null, participanteId.value(), habitoId.value(),
                cambiadoEl, ACCION_EDICION_PARTICIPANTE, horaDisparo, horaLimite, ahora));
    }
}
