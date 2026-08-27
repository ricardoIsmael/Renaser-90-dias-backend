package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.horarioadmin.ActualizarHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.ConsultarHorariosDeHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.CrearHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.EliminarHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** Panel admin de horarios por defecto del catalogo (hueco #11). */
@Service
public class HorarioHabitoAdminService implements ConsultarHorariosDeHabitoUseCase, CrearHorarioHabitoUseCase,
        ActualizarHorarioHabitoUseCase, EliminarHorarioHabitoUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadPort;
    private final SaveHorarioHabitoPort savePort;
    private final HabitoAdminGuard guard;
    private final Clock clock;

    public HorarioHabitoAdminService(LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadPort,
                                      SaveHorarioHabitoPort savePort, HabitoAdminGuard guard, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    public List<HorarioHabito> listar(UserId actorId, HabitoId habitoId) {
        guard.requireAdmin(actorId);
        requireHabito(habitoId);
        return loadPort.porHabito(habitoId);
    }

    @Override
    @Transactional
    public HorarioHabito crear(CrearHorarioHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        HorarioHabito horario = HorarioHabito.crear(command.habitoId(), command.diaInicio(), command.diaFin(),
                command.tipoDia(), command.horaDisparo(), command.horaLimite(), clock.now());
        return savePort.save(horario);
    }

    @Override
    @Transactional
    public HorarioHabito actualizar(ActualizarHorarioHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        HorarioHabito horario = requireHorario(command.horarioId());
        int diaInicio = command.diaInicio() != null ? command.diaInicio() : horario.diaInicio();
        Integer diaFin = command.limpiarDiaFin() ? null
                : (command.diaFin() != null ? command.diaFin() : horario.diaFin());
        var tipoDia = command.tipoDia() != null ? command.tipoDia() : horario.tipoDia();
        horario.actualizarRango(diaInicio, diaFin, tipoDia, clock.now());

        var horaDisparo = command.limpiarHoraDisparo() ? null
                : (command.horaDisparo() != null ? command.horaDisparo() : horario.horaDisparo());
        var horaLimite = command.limpiarHoraLimite() ? null
                : (command.horaLimite() != null ? command.horaLimite() : horario.horaLimite());
        horario.actualizarHoras(horaDisparo, horaLimite, clock.now());

        return savePort.save(horario);
    }

    @Override
    @Transactional
    public void eliminar(EliminarHorarioHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHorario(command.horarioId());
        savePort.eliminar(command.horarioId());
    }

    private void requireHabito(HabitoId id) {
        loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
    }

    private HorarioHabito requireHorario(HorarioHabitoId id) {
        return loadPort.byId(id).orElseThrow(() -> new NoSuchElementException("Horario no encontrado: " + id));
    }
}
