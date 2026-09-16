package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
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
 * Suma un aprendiz a un grupo SIN sacarlo de los que ya tiene (D-139).
 *
 * <p><b>Por qué es una clase aparte y no un método más de {@code ComposicionDeCelulaService}.</b>
 * Dos razones. La primera es de comportamiento: ese servicio existe para <i>trasladar</i> —su
 * {@code asignar} cierra todas las pertenencias vigentes antes de abrir la nueva, deliberadamente—
 * y meter acá un segundo camino que hace lo contrario habría terminado en un {@code if} dentro de
 * ese método, que es como se rompe el traslado sin que nadie lo note. La segunda es de tamaño:
 * {@code ComposicionDeCelulaService} ya pasa el techo de 300 líneas de {@code .claude/rules/01}.
 *
 * <p><b>Los guards están copiados, no compartidos</b>, siguiendo el criterio de D-126: un guard que
 * sirve a dos autorizaciones distintas es como se abren los agujeros que después nadie encuentra.
 * Acá son los mismos porque hoy las dos operaciones piden lo mismo (ADMIN/ALCHEMIST activo sobre
 * un aprendiz ACTIVO), y si mañana una de las dos cambia, cambia sola.
 *
 * <p><b>Lo que NO hace, y es lo importante:</b> no mueve
 * {@code participantes_programa.celula_id}. Esa columna es UNA sola y siete lecturas del producto
 * dependen de ella; sigue nombrando al grupo PRINCIPAL —el primero— y un alta adicional no lo
 * cambia. La única escritura posible es estrenarla cuando está vacía, porque entonces el grupo que
 * se suma <i>es</i> el primero (ver {@link #estrenarPunteroSiEstaVacio}).
 */
@Service
public class SumarAprendizAGrupoService implements SumarAprendizAGrupoUseCase {

    private final LoadCelulaPort loadCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    private final UserSummaryFinder userSummaryFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ConsultarCelulasUseCase consultarCelulas;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public SumarAprendizAGrupoService(LoadCelulaPort loadCelulaPort, LoadAsignacionesPort loadAsignacionesPort,
                                       SaveAsignacionPort saveAsignacionPort,
                                       LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                                       ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort,
                                       UserSummaryFinder userSummaryFinder,
                                       AsignacionCelulaPort asignacionCelulaPort,
                                       ConsultarCelulasUseCase consultarCelulas,
                                       ApplicationEventPublisher eventos, Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.consultarCelulaDeParticipantePort = consultarCelulaDeParticipantePort;
        this.userSummaryFinder = userSummaryFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.consultarCelulas = consultarCelulas;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public CelulaDetalle sumar(SumarAprendizAGrupoCommand command) {
        requireAdmin(command.actorId());
        Celula destino = requireCelula(command.celulaId());
        requireAprendizElegible(command.traineeId());

        Instant ahora = clock.now();
        ConjuntoAsignaciones delAprendiz = ConjuntoAsignaciones.de(loadAsignacionesPort.porUsuario(command.traineeId()));
        if (delAprendiz.aprendicesVigentesEn(destino.id(), ahora).contains(command.traineeId())) {
            // Ya está adentro —por este camino, por el traslado o por la bienvenida—: sumarlo otra
            // vez no es un error del administrador, es un pedido que ya está cumplido.
            return detalle(command.actorId(), destino.id());
        }
        requireCupoDisponible(destino, ahora);

        abrirPertenenciaAdicional(command, ahora, claveDeSuma(command, entradasPreviasA(delAprendiz, destino.id())));
        estrenarPunteroSiEstaVacio(command.traineeId(), destino.id(), ahora);

        /* Solo se avisa del DESTINO: a diferencia del traslado, acá no hay grupo de origen que
           quede sin este integrante. Es el aviso que reconcilia los participantes del chat. */
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.id().value(), ahora));
        return detalle(command.actorId(), destino.id());
    }

    /**
     * La clave identifica UNA entrada concreta a UN grupo: el par persona-grupo más cuántas veces
     * esa persona ya entró a ese grupo.
     *
     * <p>El contador no es decorativo. {@code asignaciones_celula_operacion_uk} es único por
     * (clave, célula, usuario, función), así que con una clave fija —solo persona y grupo— a quien
     * se suma, se retira y se vuelve a sumar al mismo grupo no se le podría reabrir la pertenencia
     * nunca más: chocaría contra su propia fila cerrada. Y sigue protegiendo del doble clic, que es
     * para lo que está: dos peticiones simultáneas leen el mismo historial, calculan el mismo
     * número y la segunda muere contra el índice en vez de dejar dos membresías.
     */
    private static String claveDeSuma(SumarAprendizAGrupoCommand command, int entradasPrevias) {
        return "suma-a-grupo|" + command.traineeId().value() + "|" + command.celulaId().value()
                + "|" + entradasPrevias;
    }

    private static int entradasPreviasA(ConjuntoAsignaciones delAprendiz, CelulaId celulaId) {
        return (int) delAprendiz.todas().stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.celulaId().equals(celulaId))
                .count();
    }

    /**
     * Abre el intervalo nuevo y NO cierra ninguno: ahí está toda la diferencia con el traslado.
     *
     * <p>Se verifica con {@code verificarPuedeSumar} y no con {@code verificarPuedeAbrir}: lo único
     * que sigue prohibido es estar dos veces en el MISMO grupo. La base repite esa regla desde
     * {@code V56} ({@code asignaciones_una_vez_en_cada_grupo}), porque un check-then-insert pierde
     * la carrera contra otra transacción.
     */
    private void abrirPertenenciaAdicional(SumarAprendizAGrupoCommand command, Instant ahora, String clave) {
        AsignacionCelula nueva = AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), command.celulaId(),
                command.traineeId(), FuncionAcompanamiento.APRENDIZ, ahora, MotivoAsignacion.ADMINISTRATIVO,
                command.actorId(), clave);
        ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(command.celulaId())).verificarPuedeSumar(nueva);
        saveAsignacionPort.save(nueva);
    }

    /**
     * El puntero nombra al grupo PRINCIPAL, y un alta adicional no lo mueve.
     *
     * <p>{@code participantes_programa.celula_id} es una sola columna: con dos grupos vigentes solo
     * puede nombrar a uno. Moverla al grupo recién sumado le cambiaría la respuesta a las siete
     * lecturas que hoy dependen de ella —"mi grupo" y "mis compañeros" en la app, el conteo de
     * miembros del panel, el ranking, la ficha del aprendiz, el calendario— y a un aprendiz le
     * cambiaría de grupo la pantalla principal sin que nadie se lo haya pedido. Por eso lo único
     * que se hace acá es ESTRENARLA si está vacía: en ese caso el grupo que se suma es el primero,
     * y dejarla en null sería peor —tendría grupo en el historial y "todavía no tienes grupo" en la
     * app—.
     *
     * <p>Cuál debería ser el principal cuando alguien está en varios, y qué pasa cuando se lo saca
     * justo del principal, es una decisión del dueño que todavía no está tomada (D-139).
     *
     * <p>Un aprendiz sin fila en {@code participantes_programa} hace fallar esta llamada con 404,
     * exactamente igual que el traslado (E-186 / D-135): a quien no está inscrito no le falta
     * grupo, le falta programa.
     */
    private void estrenarPunteroSiEstaVacio(UserId aprendizId, CelulaId celulaId, Instant ahora) {
        if (consultarCelulaDeParticipantePort.celulaDeUsuario(aprendizId).isPresent()) {
            return;
        }
        UserId mentorVigente = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId))
                .mentorVigenteEn(celulaId, ahora).orElse(null);
        asignacionCelulaPort.sincronizarAcompanamiento(aprendizId, celulaId.value(), mentorVigente);
    }

    /** Copia deliberada de {@code ComposicionDeCelulaService}: el cupo cuenta aprendices vigentes
     * del historial, no cabezas — mentor y soporte no ocupan lugar (D-01). Sumar a un grupo lleno
     * se rechaza igual que trasladar a un grupo lleno. */
    private void requireCupoDisponible(Celula destino, Instant ahora) {
        int capacidadPolitica = loadPoliticaMentoriaPort.porCohorte(destino.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(destino.cohorteId()))
                .capacidadCelula();
        var ocupantes = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(destino.id()))
                .ocupantesVigentesEn(destino.id(), ahora);
        if (!destino.cupo(capacidadPolitica).admiteOtroAprendiz(ocupantes)) {
            throw new AsignacionInvalidaException("El grupo " + destino.nombre() + " no tiene cupo disponible");
        }
    }

    private void requireAprendizElegible(UserId traineeId) {
        UserSummary trainee = userSummaryFinder.findById(traineeId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + traineeId));
        if (trainee.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("El aprendiz seleccionado no esta activo");
        }
        if (trainee.role() != UserRole.TRAINEE) {
            throw new IllegalArgumentException("Solo se puede sumar a un grupo a un aprendiz");
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
