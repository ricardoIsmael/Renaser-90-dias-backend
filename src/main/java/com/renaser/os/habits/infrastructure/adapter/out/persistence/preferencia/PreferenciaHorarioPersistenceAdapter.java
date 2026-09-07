package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class PreferenciaHorarioPersistenceAdapter implements LoadPreferenciaHorarioPort, SavePreferenciaHorarioPort {

    private final SpringDataPreferenciaHorarioRepository repository;
    private final PreferenciaHorarioPersistenceMapper mapper;
    private final SpringDataHorarioPorFechaRepository fechasRepository;
    private final SpringDataCambioHorarioPendienteRepository pendientesRepository;

    PreferenciaHorarioPersistenceAdapter(SpringDataPreferenciaHorarioRepository repository,
                                          PreferenciaHorarioPersistenceMapper mapper,
                                          SpringDataHorarioPorFechaRepository fechasRepository,
                                          SpringDataCambioHorarioPendienteRepository pendientesRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.fechasRepository = fechasRepository;
        this.pendientesRepository = pendientesRepository;
    }

    @Override
    public Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<PreferenciaHorario> porParticipanteYHabitos(UserId participanteId, Collection<HabitoId> habitoIds) {
        if (habitoIds.isEmpty()) {
            return List.of();
        }
        List<UUID> valores = habitoIds.stream().map(HabitoId::value).toList();
        return repository.findByParticipanteIdAndHabitoIdIn(participanteId.value(), valores).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public PreferenciaHorario save(PreferenciaHorario preferencia) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(preferencia)));
    }
    @Override
    public Optional<PreferenciaHorario> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId,
                                                                  LocalDate fecha) {
        return porParticipanteHabitosYFecha(participanteId, List.of(habitoId), fecha).stream().findFirst();
    }

    @Override
    public List<PreferenciaHorario> porParticipanteHabitosYFecha(UserId participanteId, Collection<HabitoId> habitoIds,
                                                               LocalDate fecha) {
        if (habitoIds.isEmpty()) return List.of();
        Map<HabitoId, PreferenciaHorario> efectivos = new HashMap<>();
        porParticipanteYHabitos(participanteId, habitoIds).forEach(p -> efectivos.put(p.habitoId(), p));
        // Conserva los cambios generales de clientes anteriores, solo desde su fecha efectiva.
        for (var p : pendientesRepository.findByParticipanteId(participanteId.value())) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            if (habitoIds.contains(id) && !p.getFechaEfectiva().isAfter(fecha)) {
                var anterior = efectivos.get(id);
                Integer minutos = anterior != null ? anterior.minutosRecordatorio() : null;
                if (p.getMinutosRecordatorio() != null) minutos = p.getMinutosRecordatorio().intValue();
                efectivos.put(id, PreferenciaHorario.rehydrate(participanteId, id, p.getHoraDisparo(), p.getHoraLimite(),
                        p.getRecordatorioActivo() != null ? p.getRecordatorioActivo()
                                : anterior != null && anterior.recordatorioActivo(),
                        minutos,
                        p.getCreadoEn(), p.getCreadoEn()));
            }
        }
        var ids = habitoIds.stream().map(HabitoId::value).toList();
        for (var p : fechasRepository.findByParticipanteIdAndHabitoIdInAndFecha(participanteId.value(), ids, fecha)) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            // V38: una fila que solo APAGA el dia no trae hora, y no tiene que pisar la que ya
            // regia. Sin este corte, apagar el jueves le borraria el horario al jueves.
            if (p.getHoraDisparo() == null) {
                continue;
            }
            efectivos.put(id, PreferenciaHorario.rehydrate(participanteId, id, p.getHoraDisparo(), p.getHoraLimite(),
                    p.isRecordatorioActivo(), p.getMinutosRecordatorio() == null ? null : p.getMinutosRecordatorio().intValue(),
                    p.getCreadoEn(), p.getActualizadoEn()));
        }
        return List.copyOf(efectivos.values());
    }

    @Override
    public void saveParaFecha(HorarioPorFecha horario) {
        var p = horario.preferencia();
        var key = new HorarioPorFechaPk(p.participanteId().value(), p.habitoId().value(), horario.fecha());
        var creadoEn = fechasRepository.findById(key).map(HorarioPorFechaJpaEntity::getCreadoEn).orElse(p.creadoEn());
        fechasRepository.saveAndFlush(new HorarioPorFechaJpaEntity(p.participanteId().value(), p.habitoId().value(),
                horario.fecha(), p.horaDisparo(), horario.activo(), p.horaLimite(), p.recordatorioActivo(),
                p.minutosRecordatorio() == null ? null : p.minutosRecordatorio().shortValue(), creadoEn, p.actualizadoEn()));
    }

    @Override
    public void borrarParaFecha(UserId participanteId, HabitoId habitoId, LocalDate fecha) {
        var key = new HorarioPorFechaPk(participanteId.value(), habitoId.value(), fecha);
        // `existsById` antes de borrar: volver a encender algo que nunca se apago es una operacion
        // legitima y no tiene que reventar. Idempotente, igual que el DELETE de `habit-unlocks`.
        if (fechasRepository.existsById(key)) {
            fechasRepository.deleteById(key);
            fechasRepository.flush();
        }
    }

    @Override
    public List<HabitoId> habitosApagadosEn(UserId participanteId, LocalDate fecha) {
        return fechasRepository.apagadosEn(participanteId.value(), fecha).stream().map(HabitoId::of).toList();
    }

    @Override
    public List<HabitoId> habitosConHorarioEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        return fechasRepository.habitosEntre(participanteId.value(), desde, hasta).stream().map(HabitoId::of).toList();
    }
}
