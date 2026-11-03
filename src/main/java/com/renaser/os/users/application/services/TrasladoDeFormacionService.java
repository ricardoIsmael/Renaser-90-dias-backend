package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AprendizTrasladadoDeFormacionEvent;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.PuntajeDelAprendizFinder;
import com.renaser.os.users.api.PuntajeDelAprendizFinder.PuntajeDelAprendiz;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.ParticipacionesAnterioresPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.formacion.ParticipacionAnterior;
import com.renaser.os.users.domain.model.formacion.ReglasDeTraslado;
import com.renaser.os.users.domain.model.formacion.ReglasDeTraslado.Decision;
import com.renaser.os.users.domain.model.formacion.ReglasDeTraslado.Tipo;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Pasa a un aprendiz a otra formación (spec 022, Q1-A, Q8-A). En UNA transacción: decide con {@link ReglasDeTraslado}, guarda la foto de lo que fue,
 * reinicia su participación y avisa a los demás módulos con {@link AprendizTrasladadoDeFormacionEvent} (grupo y puntos). Los registros de hábito,
 * evidencias y publicaciones no se tocan.
 */
@Service
class TrasladoDeFormacionService implements TrasladarAprendizDeFormacionUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final LoadUserPort usuarios;
    private final LoadParticipacionProgramaPort participaciones;
    private final SaveParticipacionProgramaPort guardarParticipacion;
    private final CatalogoDeFormacionesPort catalogo;
    private final ParticipacionesAnterioresPort historial;
    private final PuntajeDelAprendizFinder puntajes;
    private final ApplicationEventPublisher eventos;
    private final IdGenerator ids;
    private final Clock clock;

    TrasladoDeFormacionService(RequireAdminGuard requireAdminGuard, LoadUserPort usuarios,
                               LoadParticipacionProgramaPort participaciones, SaveParticipacionProgramaPort guardarParticipacion,
                               CatalogoDeFormacionesPort catalogo, ParticipacionesAnterioresPort historial,
                               PuntajeDelAprendizFinder puntajes, ApplicationEventPublisher eventos, IdGenerator ids, Clock clock) {
        this.requireAdminGuard = requireAdminGuard;
        this.usuarios = usuarios;
        this.participaciones = participaciones;
        this.guardarParticipacion = guardarParticipacion;
        this.catalogo = catalogo;
        this.historial = historial;
        this.puntajes = puntajes;
        this.eventos = eventos;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void trasladar(TrasladarCommand command) {
        ParticipacionPrograma participacion = participaciones.byParticipanteId(command.aprendiz())
                .orElseThrow(() -> new NoSuchElementException("Participante no inscripto en el programa: " + command.aprendiz()));
        requireAdminGuard.requireAdminActivo(command.actor());
        exigirAprendiz(command.aprendiz());
        FormacionConfigurada destino = catalogo.de(command.destino());
        Decision decision = ReglasDeTraslado.decidir(participacion, command.destino(), command.duracionDias(),
                command.darPorTerminada(), command.motivo(), new HashSet<>(destino.duracionesDias()), destino.activa());
        Formacion desde = participacion.formacion();
        UUID celulaAnterior = participacion.celulaId();
        if (decision.tipo() == Tipo.CON_HISTORIA) {
            historial.guardar(laFoto(participacion, command, decision));
        }
        participacion.trasladarA(command.destino(), command.duracionDias(), clock);
        guardarParticipacion.save(participacion);
        eventos.publishEvent(new AprendizTrasladadoDeFormacionEvent(command.aprendiz(), desde, command.destino(), celulaAnterior,
                command.actor(), clock.now()));
    }

    private void exigirAprendiz(UserId aprendiz) {
        UserRole rol = usuarios.byId(aprendiz).map(u -> u.role()).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + aprendiz));
        if (rol != UserRole.TRAINEE) {
            throw new IllegalStateException("Solo se traslada a los aprendices. El personal trabaja en las formaciones que se le asignan.");
        }
    }

    private ParticipacionAnterior laFoto(ParticipacionPrograma p, TrasladarCommand command, Decision decision) {
        Optional<PuntajeDelAprendiz> puntaje = puntajes.de(command.aprendiz());
        LocalDate hoy = clock.now().atZone(p.timezone()).toLocalDate();
        return new ParticipacionAnterior(ids.newId(), command.aprendiz(), p.formacion(), p.duracionDias(),
                p.estaActivado() ? p.fechaInicio() : null, hoy, p.diaVigente(clock), decision.resultado().orElseThrow(), decision.motivo(),
                puntaje.map(PuntajeDelAprendiz::puntosLiga).orElse(null), puntaje.map(PuntajeDelAprendiz::coherencia).orElse(null),
                puntaje.map(PuntajeDelAprendiz::rachaMaxima).orElse(null), command.destino(), command.actor(), clock.now());
    }
}
