package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Autoservicio: el participante ve el catalogo activo + sus propios habitos PERSONAL, y puede
 * crear nuevos habitos PERSONAL (§3, docs/informes/habits-eleccion-y-personales.md). Reusa el
 * agregado {@link Habito} y su {@link SaveHabitoPort} — no hay un servicio admin duplicado para
 * esto: {@link com.renaser.os.habits.application.services.HabitoAdminService} sigue siendo solo
 * para el catalogo SISTEMA.
 */
@Service
public class MisHabitosService implements ConsultarMisHabitosUseCase, CrearHabitoPersonalUseCase {

    private final LoadHabitoPort loadPort;
    private final SaveHabitoPort savePort;
    private final SaveHorarioHabitoPort saveHorarioPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public MisHabitosService(LoadHabitoPort loadPort, SaveHabitoPort savePort,
                              SaveHorarioHabitoPort saveHorarioPort,
                              ConsultarProgresoParticipanteHabitsPort progresoPort, Clock clock,
                              IdGenerator idGenerator) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.saveHorarioPort = saveHorarioPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<Habito> consultar(UserId actor) {
        List<Habito> habitos = new ArrayList<>(loadPort.catalogoActivo());
        habitos.addAll(loadPort.personalesActivosDe(actor));
        return habitos;
    }

    /**
     * {@code ambito}/{@code participanteId} NO vienen del comando — {@link Habito#crearPersonal}
     * los fija siempre a PERSONAL/{@code command.actorId()} (blindaje de mass-assignment,
     * CLAUDE.MD §5.3.3, ver javadoc de {@link CrearHabitoPersonalUseCase}).
     *
     * <p><b>Atomicidad (docs/informes/habits-personal-con-horario.md):</b> el {@link Habito} y
     * su {@link HorarioHabito} se guardan en el MISMO metodo {@code @Transactional} — no hace
     * falta {@code TransactionTemplate} ni una segunda anotacion, es el mismo patron que ya usa
     * {@code HorarioHabitoAdminService.crear} para habito+horario. Si {@code saveHorarioPort.save}
     * falla (ej. el invariante de {@link HorarioHabito#crear} rechaza {@code horaLimite} anterior
     * a {@code horaDisparo}, o un {@code diaPrograma} fuera de 1..90), Postgres deshace tambien el
     * {@code savePort.save(habito)} anterior: nunca queda un habito personal sin horario, que es
     * exactamente el bug que este cambio cierra.
     *
     * <p><b>{@code diaInicio}/{@code tipoDia} elegidos</b> (ver informe para la traza completa
     * contra {@code aplicaEnDia}): {@code diaInicio = progreso.diaPrograma()} (aplica desde HOY,
     * el dia de programa del participante en el momento de la creacion, nunca en el pasado),
     * {@code diaFin = null} (abierto, no vence), {@code tipoDia = TipoDia.TODOS} (aplica
     * cualquier dia de la semana — un habito personal no tiene el concepto de "solo domingo" ni
     * "solo dia de disciplina" que si tiene el catalogo).
     */
    @Override
    @Transactional
    public Habito crear(CrearHabitoPersonalCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        HabitoId id = HabitoId.of(idGenerator.newId());
        Habito habito = Habito.crearPersonal(id, command.actorId(), command.titulo(), command.tipo(),
                command.categoriaClave(), command.plantilla(), command.etiquetaMeta(), clock.now());
        Habito guardado = savePort.save(habito);

        HorarioHabitoId horarioId = HorarioHabitoId.of(idGenerator.newId());
        HorarioHabito horario = HorarioHabito.crear(horarioId, id, progreso.diaPrograma(), null, TipoDia.TODOS,
                command.horaDisparo(), command.horaLimite(), clock.now());
        saveHorarioPort.save(horario);

        return guardado;
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        var progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
