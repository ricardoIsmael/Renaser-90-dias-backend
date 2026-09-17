package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Suma un grupo a los que un mentor ya lidera, sin quitarle ninguno (D-141).
 *
 * <p><b>Por qué es una clase aparte y no un método más de {@code ComposicionDeCelulaService}.</b>
 * Las mismas dos razones que dejó escritas {@link SumarAprendizAGrupoService}. La de
 * comportamiento pesa todavía más acá: el {@code asignar} de aquel servicio no solo cierra la
 * asignación anterior del mentor, además deja a cada grupo que abandona <b>sin mentor</b> y
 * repunta a sus aprendices. Meter un {@code if} adentro de ese método para saltear esos efectos
 * era la forma segura de romper el traslado sin que nadie lo notara. La de tamaño también sigue
 * vigente: {@code ComposicionDeCelulaService} ya pasa el techo de 300 líneas de
 * {@code .claude/rules/01}.
 *
 * <p><b>Lo que sí hace y el alta del aprendiz no:</b> escribe {@code celulas.mentor_id} del grupo
 * destino y repunta a sus aprendices. No es una asimetría caprichosa — es que las dos columnas
 * significan cosas distintas. {@code celulas.mentor_id} nombra <i>al mentor de ese grupo</i>, y ese
 * grupo efectivamente acaba de estrenar mentor; {@code participantes_programa.celula_id} nombra
 * <i>el grupo principal de una persona</i>, y sumarle un grupo a alguien no cambia cuál es el
 * principal.
 */
@Service
public class SumarMentorAGrupoService implements SumarMentorAGrupoUseCase {

    private final LoadCelulaPort loadCelulaPort;
    private final SaveCelulaPort saveCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final ExistePerfilMentorPort existePerfilMentorPort;
    private final UserSummaryFinder userSummaryFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ConsultarCelulasUseCase consultarCelulas;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public SumarMentorAGrupoService(LoadCelulaPort loadCelulaPort, SaveCelulaPort saveCelulaPort,
                                     LoadAsignacionesPort loadAsignacionesPort, SaveAsignacionPort saveAsignacionPort,
                                     ExistePerfilMentorPort existePerfilMentorPort,
                                     UserSummaryFinder userSummaryFinder, AsignacionCelulaPort asignacionCelulaPort,
                                     ConsultarCelulasUseCase consultarCelulas, ApplicationEventPublisher eventos,
                                     Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.saveCelulaPort = saveCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.existePerfilMentorPort = existePerfilMentorPort;
        this.userSummaryFinder = userSummaryFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.consultarCelulas = consultarCelulas;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public CelulaDetalle sumar(SumarMentorAGrupoCommand command) {
        requireAdmin(command.actorId());
        Celula destino = requireCelula(command.celulaId());
        requireMentorElegible(command.mentorId());

        Instant ahora = clock.now();
        ConjuntoAsignaciones delGrupo = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(destino.id()));
        if (delGrupo.mentorVigenteEn(destino.id(), ahora).filter(command.mentorId()::equals).isPresent()) {
            // Ya lo lidera: pedirlo otra vez no es un error del administrador, es un pedido cumplido.
            return detalle(command.actorId(), destino.id());
        }

        abrirJefaturaAdicional(command, ahora);
        destino.asignarMentor(command.mentorId(), ahora);
        saveCelulaPort.save(destino);
        repuntarAprendicesDe(destino.id(), ahora);

        /* Solo se avisa del DESTINO: a diferencia del traslado, acá no hay grupo de origen que
           quede sin mentor. Es el aviso que reconcilia los participantes del chat. */
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.id().value(), ahora));
        return detalle(command.actorId(), destino.id());
    }

    /**
     * Abre el intervalo nuevo y NO cierra ninguno: ahí está toda la diferencia con el traslado.
     *
     * <p>Se verifica con {@code verificarPuedeSumar} y no con {@code verificarPuedeAbrir}: lo único
     * que sigue prohibido es que el grupo tenga dos mentores vigentes. La base repite esa regla
     * desde V45 con {@code asignaciones_un_mentor_por_celula} —que V58 conserva a propósito—,
     * porque un check-then-insert pierde la carrera contra otra transacción.
     */
    private void abrirJefaturaAdicional(SumarMentorAGrupoCommand command, Instant ahora) {
        AsignacionCelula nueva = AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), command.celulaId(),
                command.mentorId(), FuncionAcompanamiento.MENTOR, ahora, MotivoAsignacion.ADMINISTRATIVO,
                command.actorId(), claveDeSuma(command, jefaturasPreviasA(command)));
        ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(command.celulaId())).verificarPuedeSumar(nueva);
        saveAsignacionPort.save(nueva);
    }

    /**
     * La clave identifica UNA llegada concreta a UN grupo: el par mentor-grupo más cuántas veces
     * ese mentor ya lo lideró.
     *
     * <p>El contador no es decorativo, y la razón es la misma que en {@code SumarAprendizAGrupoService}:
     * {@code asignaciones_celula_operacion_uk} es único por (clave, célula, usuario, función), así
     * que con una clave fija a un mentor que entra, sale y vuelve al mismo grupo no se le podría
     * reabrir la jefatura nunca más — chocaría contra su propia fila cerrada. Y sigue cubriendo el
     * doble clic: dos peticiones simultáneas calculan el mismo número y la segunda muere contra el
     * índice en vez de dejar dos jefaturas.
     *
     * <p>Se usa un prefijo propio (`suma-mentor|`) y no el `mentor-manual|` del traslado: son dos
     * operaciones distintas y compartir espacio de claves haría que una tapara a la otra.
     */
    private static String claveDeSuma(SumarMentorAGrupoCommand command, int jefaturasPrevias) {
        return "suma-mentor|" + command.mentorId().value() + "|" + command.celulaId().value()
                + "|" + jefaturasPrevias;
    }

    private int jefaturasPreviasA(SumarMentorAGrupoCommand command) {
        return (int) loadAsignacionesPort.porUsuario(command.mentorId()).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.celulaId().equals(command.celulaId()))
                .count();
    }

    /**
     * Los aprendices del grupo pasan a apuntar al mentor que acaba de llegar.
     *
     * <p>Copia deliberada de {@code ComposicionDeCelulaService.sincronizarAprendicesDe}: ese
     * puntero es quien autoriza la evidencia del aprendiz, y dejarlo en el mentor anterior —o en
     * null— después de que el grupo estrenó mentor sería mentira. Se lee el mentor vigente del
     * grupo en vez de usar el del comando para que la fuente siga siendo una sola.
     */
    private void repuntarAprendicesDe(CelulaId celulaId, Instant ahora) {
        ConjuntoAsignaciones composicion = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId));
        UserId mentorVigente = composicion.mentorVigenteEn(celulaId, ahora).orElse(null);
        for (UserId aprendiz : composicion.aprendicesVigentesEn(celulaId, ahora)) {
            asignacionCelulaPort.sincronizarAcompanamiento(aprendiz, celulaId.value(), mentorVigente);
        }
    }

    /** Copia deliberada de {@code ComposicionDeCelulaService} (criterio de D-126: un guard que
     * sirve a dos autorizaciones distintas es como se abren los agujeros que despues nadie
     * encuentra). Hoy piden lo mismo; si manana una cambia, cambia sola. */
    private void requireMentorElegible(UserId mentorId) {
        UserSummary lider = userSummaryFinder.findById(mentorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + mentorId));
        if (lider.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("El usuario seleccionado no esta activo");
        }
        if (lider.role() != UserRole.MENTOR && lider.role() != UserRole.ADMIN && lider.role() != UserRole.ALCHEMIST) {
            throw new IllegalArgumentException("El usuario seleccionado no puede liderar una celula");
        }
        if (!existePerfilMentorPort.existe(mentorId)) {
            throw new IllegalStateException(
                    "El usuario todavia no tiene un perfil de mentor (perfiles_mentor) — debe crearse desde "
                            + "el modulo de usuarios antes de poder liderar una celula");
        }
    }

    private CelulaDetalle detalle(UserId actorId, CelulaId celulaId) {
        return consultarCelulas.obtener(actorId, celulaId);
    }

    private Celula requireCelula(CelulaId id) {
        return loadCelulaPort.porId(id).orElseThrow(() -> new NoSuchElementException("Celula no encontrada: " + id));
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran celulas");
        }
    }
}
