package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
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
    private final SpringDataHorarioSemanalRepository semanalRepository;
    private final SpringDataCambioHorarioPendienteRepository pendientesRepository;

    PreferenciaHorarioPersistenceAdapter(SpringDataPreferenciaHorarioRepository repository,
                                          PreferenciaHorarioPersistenceMapper mapper,
                                          SpringDataHorarioPorFechaRepository fechasRepository,
                                          SpringDataHorarioSemanalRepository semanalRepository,
                                          SpringDataCambioHorarioPendienteRepository pendientesRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.fechasRepository = fechasRepository;
        this.semanalRepository = semanalRepository;
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
        // V39: la hora propia de ESE dia de la semana. Va entre el cambio general y la excepcion de
        // fecha exacta, que es lo que corresponde por especificidad: "los lunes a las 5" pisa al
        // horario general, y "el lunes 8 a las 4" pisa a los dos.
        //
        // El respaldo es POR CAMPO y no por objeto: una fila que solo fija la hora de disparo
        // conserva la hora limite de la capa de abajo. Reemplazar el objeto entero le borraria la
        // hora limite a los habitos que si vencen dentro del dia.
        short diaIso = (short) fecha.getDayOfWeek().getValue();
        for (var p : semanalRepository.findByParticipanteIdAndHabitoIdInAndDiaSemana(
                participanteId.value(), ids, diaIso)) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            var anterior = efectivos.get(id);
            efectivos.put(id, PreferenciaHorario.rehydrate(participanteId, id, p.getHoraDisparo(),
                    p.getHoraLimite() != null ? p.getHoraLimite() : (anterior != null ? anterior.horaLimite() : null),
                    anterior != null && anterior.recordatorioActivo(),
                    anterior != null ? anterior.minutosRecordatorio() : null,
                    p.getCreadoEn(), p.getActualizadoEn()));
        }
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
    public List<HorarioSemanal> horarioSemanalDe(UserId participanteId, HabitoId habitoId) {
        return semanalRepository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value()).stream()
                .map(p -> new HorarioSemanal(DayOfWeek.of(p.getDiaSemana()),
                        PreferenciaHorario.rehydrate(participanteId, habitoId, p.getHoraDisparo(), p.getHoraLimite(),
                                false, null, p.getCreadoEn(), p.getActualizadoEn())))
                .toList();
    }

    @Override
    public void saveParaDiaSemana(UserId participanteId, HabitoId habitoId, HorarioSemanal horario) {
        var p = horario.preferencia();
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(),
                (short) horario.diaSemana().getValue());
        var creadoEn = semanalRepository.findById(key).map(HorarioSemanalJpaEntity::getCreadoEn).orElse(p.creadoEn());
        semanalRepository.saveAndFlush(new HorarioSemanalJpaEntity(participanteId.value(), habitoId.value(),
                (short) horario.diaSemana().getValue(), p.horaDisparo(), p.horaLimite(), creadoEn,
                p.actualizadoEn()));
    }

    @Override
    public void borrarParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana) {
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(), (short) diaSemana.getValue());
        // `existsById` antes de borrar: quitar una hora que nunca se puso es legitimo y no tiene
        // que reventar. Idempotente, igual que el resto de los borrados de este modulo.
        if (semanalRepository.existsById(key)) {
            semanalRepository.deleteById(key);
            semanalRepository.flush();
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
