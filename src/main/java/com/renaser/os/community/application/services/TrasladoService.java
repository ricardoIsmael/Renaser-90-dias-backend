package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.acompanamiento.TrasladarAprendicesUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.DecisionTraslado;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.DestinoTraslado;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.GrupoCandidato;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeTraslado.SituacionAprendiz;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Entrada a recepción y paso al grupo estable.
 *
 * <p>La decisión de qué corresponde es pura y vive en {@link PlanificadorDeTraslado}; acá solo
 * se aplica. La separación importa porque el caso difícil —qué día local es para este
 * participante— se prueba sin base ni reloj.
 */
@Service
public class TrasladoService implements TrasladarAprendicesUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrasladoService.class);

    private final LoadCelulaPort loadCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TrasladoService(LoadCelulaPort loadCelulaPort, LoadAsignacionesPort loadAsignacionesPort,
                            SaveAsignacionPort saveAsignacionPort,
                            LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                            ParticipacionProgramaFinder participacionProgramaFinder,
                            AsignacionCelulaPort asignacionCelulaPort, ApplicationEventPublisher eventos,
                            Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Una transacción POR APRENDIZ, no una por lote. Si a uno le falla el traslado —porque
     * perdió la carrera por el último cupo, por ejemplo— los demás del lote se procesan igual
     * (plan.md §4, V26).
     */
    @Override
    public int procesarLote(int tamanoLote) {
        int movidos = 0;
        List<UserId> padron = participacionProgramaFinder.participantesInscritosActivos();
        for (UserId aprendiz : padron.stream().limit(tamanoLote).toList()) {
            try {
                if (ubicar(aprendiz).grupoId() != null) {
                    movidos++;
                }
            } catch (RuntimeException e) {
                log.warn("[community.TrasladoService] no se pudo ubicar a {}; sigue el lote", aprendiz, e);
            }
        }
        return movidos;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoTraslado ubicar(UserId aprendizId) {
        Instant ahora = clock.now();

        Optional<ParticipacionPrograma> quizaParticipacion =
                participacionProgramaFinder.deParticipante(aprendizId);
        if (quizaParticipacion.isEmpty()) {
            return new ResultadoTraslado(aprendizId, DestinoTraslado.SIN_CAMBIO.name(), null,
                    "El usuario no existe");
        }
        ParticipacionPrograma participacion = quizaParticipacion.get();

        Optional<Celula> celulaActual = Optional.ofNullable(participacion.celulaId())
                .flatMap(id -> loadCelulaPort.porId(CelulaId.of(id)));
        Optional<CohorteId> cohorte = celulaActual.map(Celula::cohorteId);
        if (cohorte.isEmpty()) {
            // Sin celula previa no hay cohorte de la cual sacar politica ni recepcion. Asignar
            // cohorte es una decision administrativa, no algo que un job pueda adivinar.
            return new ResultadoTraslado(aprendizId, DestinoTraslado.SIN_CAMBIO.name(), null,
                    "Sin cohorte asignada: no hay recepcion a la cual entrar");
        }

        PoliticaMentoria politica = loadPoliticaMentoriaPort.porCohorte(cohorte.get())
                .orElseGet(() -> PoliticaMentoria.porDefecto(cohorte.get()));

        SituacionAprendiz situacion = new SituacionAprendiz(
                participacion.diaPrograma(), participacion.activado(),
                celulaActual.map(Celula::id).orElse(null), celulaActual.map(Celula::tipo).orElse(null));

        DecisionTraslado decision = PlanificadorDeTraslado.decidir(situacion, politica,
                politica.celulaRecepcionId(), candidatos(cohorte.get(), politica, ahora));

        return switch (decision.destino()) {
            case RECEPCION, GRUPO_ESTABLE -> mover(aprendizId, decision, situacion, ahora);
            case ESPERANDO_GRUPO -> {
                log.warn("[community.TrasladoService] {} espera grupo en la cohorte {}: sin cupo",
                        aprendizId, cohorte.get());
                yield new ResultadoTraslado(aprendizId, decision.destino().name(), null, decision.motivo());
            }
            case SIN_RECEPCION_CONFIGURADA -> {
                log.warn("[community.TrasladoService] la cohorte {} no tiene recepcion designada", cohorte.get());
                yield new ResultadoTraslado(aprendizId, decision.destino().name(), null, decision.motivo());
            }
            case SIN_CAMBIO -> new ResultadoTraslado(aprendizId, decision.destino().name(), null,
                    decision.motivo());
        };
    }

    /**
     * Cierra la pertenencia anterior y abre la nueva en la misma transacción, y recién después
     * sincroniza los punteros de proyección. El cierre va primero porque el índice de exclusión
     * de V45 no admite dos grupos vigentes para el mismo aprendiz — igual que en la rotación.
     */
    private ResultadoTraslado mover(UserId aprendizId, DecisionTraslado decision, SituacionAprendiz situacion,
                                     Instant ahora) {
        CelulaId destino = decision.grupoDestino();
        String clave = "traslado:" + aprendizId.value() + ":" + destino.value();

        Optional<AsignacionCelula> yaHecha = loadAsignacionesPort.porClaveOperacion(clave);
        if (yaHecha.isPresent()) {
            return new ResultadoTraslado(aprendizId, decision.destino().name(), destino.value(),
                    "Ya estaba aplicado");
        }

        if (situacion.celulaActual() != null) {
            loadAsignacionesPort.porUsuario(aprendizId).stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                    .filter(a -> a.vigenteEn(ahora))
                    .forEach(anterior -> {
                        anterior.cerrar(ahora, MotivoAsignacion.TRASLADO);
                        saveAsignacionPort.save(anterior);
                    });
        }

        MotivoAsignacion motivo = decision.destino() == DestinoTraslado.RECEPCION
                ? MotivoAsignacion.RECEPCION
                : MotivoAsignacion.TRASLADO;
        saveAsignacionPort.save(AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), destino, aprendizId,
                FuncionAcompanamiento.APRENDIZ, ahora, motivo, null, clave));

        UserId mentorDelDestino = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(destino))
                .mentorVigenteEn(destino, ahora).orElse(null);
        asignacionCelulaPort.sincronizarAcompanamiento(aprendizId, destino.value(), mentorDelDestino);

        // Los DOS grupos cambian: uno pierde a alguien y el otro lo gana. Sin el aviso del
        // origen, el chat del grupo anterior lo seguiria mostrando como integrante.
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.value(), ahora));
        if (situacion.celulaActual() != null && !situacion.celulaActual().equals(destino)) {
            eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(situacion.celulaActual().value(), ahora));
        }

        return new ResultadoTraslado(aprendizId, decision.destino().name(), destino.value(), decision.motivo());
    }

    /**
     * Ocupación real leída del historial, no del contador de la célula: el cupo se mide en
     * aprendices vigentes, y mentor, guía y soporte no ocupan lugar.
     */
    private List<GrupoCandidato> candidatos(CohorteId cohorteId, PoliticaMentoria politica, Instant ahora) {
        return loadCelulaPort.porCohorte(cohorteId).stream()
                .filter(c -> c.tipo() == TipoCelula.REGULAR)
                .map(c -> new GrupoCandidato(c.id(),
                        ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(c.id()))
                                .aprendicesVigentesEn(c.id(), ahora).size(),
                        c.cupo(politica.capacidadCelula()).maximo().orElse(Integer.MAX_VALUE)))
                .toList();
    }
}
