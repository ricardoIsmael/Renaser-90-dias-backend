package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** Panel admin de catalogo (hueco #11): alta, edicion, activar/desactivar y borrado de habitos SISTEMA. */
@Service
public class HabitoAdminService implements ConsultarCatalogoAdminUseCase, CrearHabitoUseCase, ActualizarHabitoUseCase,
        CambiarActivoHabitoUseCase, EliminarHabitoUseCase {

    private final LoadHabitoPort loadPort;
    private final SaveHabitoPort savePort;
    private final HabitoAdminGuard guard;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public HabitoAdminService(LoadHabitoPort loadPort, SaveHabitoPort savePort, HabitoAdminGuard guard, Clock clock,
                               IdGenerator idGenerator) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.guard = guard;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<Habito> listar(UserId actorId) {
        guard.requireAdmin(actorId);
        return loadPort.catalogoCompleto();
    }

    @Override
    @Transactional
    public Habito crear(CrearHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        Habito habito = Habito.crearDeSistema(HabitoId.of(idGenerator.newId()), command.titulo(), command.tipo(),
                command.detalles(), clock.now());
        return savePort.save(habito);
    }

    @Override
    @Transactional
    public Habito actualizar(ActualizarHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        habito.actualizarDetalles(command.detalles(), clock.now());
        return savePort.save(habito);
    }

    @Override
    @Transactional
    public Habito cambiarActivo(CambiarActivoHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        if (command.activo()) {
            habito.activar(clock.now());
        } else {
            habito.desactivar(clock.now());
        }
        return savePort.save(habito);
    }

    @Override
    @Transactional
    public void eliminar(EliminarHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        savePort.eliminar(command.habitoId());
    }

    private Habito requireHabito(HabitoId id) {
        return loadPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
    }
}
