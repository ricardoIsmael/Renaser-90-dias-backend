package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.mapa.CompletarEtapaMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.ConsultarMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.mapa.ReemplazarListaMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.AreaMapa;
import com.renaser.os.onboarding.domain.model.mapa.EvidenciaAccion;
import com.renaser.os.onboarding.domain.model.mapa.MomentoAccion;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * El Mapa de Renacimiento (Dia 7) sobre el motor de onboarding — fase 3 del plan
 * (docs/PLAN_MAPA_RENACIMIENTO_BACKEND.md).
 *
 * <p><b>Que hace y que NO hace este servicio.</b> Los tres objetivos, los nueve hitos, la
 * prioridad, el retorno y el compromiso son preguntas del flujo {@code mapa_dia7} y los guarda
 * {@code RespuestaService}, que ya existia. Aca viven solo las dos listas que
 * {@code respuestas_onboarding} no puede representar —tiene {@code UNIQUE (usuario_id,
 * pregunta_id)}, una respuesta por pregunta— y la marca de etapa terminada.
 *
 * <p><b>Se guarda por PASO, no por respuesta</b> (pedido del dueno del proyecto, 2026-09-08): cada
 * llamada trae la vista completa y reemplaza lo que hubiera. Es el mismo criterio que
 * {@code usePersistenciaOnboarding.guardarCapitulo} ya aplica en los otros cinco flujos, y ademas
 * es la unica forma de validar en el servidor las reglas que miran varias filas a la vez.
 */
@Service
public class MapaRenacimientoService
        implements GuardarPasoMapaUseCase, ConsultarMapaUseCase, CompletarEtapaMapaUseCase {

    /** El flujo del motor de onboarding en el que vive el Mapa (V41). */
    public static final String FLUJO = "mapa_dia7";

    private final ConsultarActorPort actorPort;
    private final LoadMapaPort loadMapaPort;
    private final ReemplazarListaMapaPort reemplazarListaMapaPort;
    private final EtapaOnboardingPort etapaOnboardingPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public MapaRenacimientoService(ConsultarActorPort actorPort, LoadMapaPort loadMapaPort,
                                    ReemplazarListaMapaPort reemplazarListaMapaPort,
                                    EtapaOnboardingPort etapaOnboardingPort, IdGenerator idGenerator, Clock clock) {
        this.actorPort = actorPort;
        this.loadMapaPort = loadMapaPort;
        this.reemplazarListaMapaPort = reemplazarListaMapaPort;
        this.etapaOnboardingPort = etapaOnboardingPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void guardarSistemaDeEjecucion(GuardarAccionesCommand command) {
        requireActorActivo(command.actorId());
        List<AccionMapa> acciones = command.acciones().stream()
                .map(entrada -> aAccion(command.actorId(), entrada))
                .toList();
        reemplazarListaMapaPort.reemplazarAcciones(command.actorId(), new AccionesDelMapa(acciones));
    }

    @Override
    @Transactional
    public void guardarProtocolosDeReemplazo(GuardarProtocolosCommand command) {
        requireActorActivo(command.actorId());
        List<ProtocoloReemplazoMapa> protocolos = command.protocolos().stream()
                .map(entrada -> aProtocolo(command.actorId(), entrada))
                .toList();
        reemplazarListaMapaPort.reemplazarProtocolos(command.actorId(), new ProtocolosDelMapa(protocolos));
    }

    @Override
    @Transactional(readOnly = true)
    public MapaDelParticipante consultar(UserId actorId) {
        requireActorActivo(actorId);
        return new MapaDelParticipante(loadMapaPort.accionesDe(actorId), loadMapaPort.protocolosDe(actorId),
                etapaOnboardingPort.flujosCompletados(actorId).contains(FLUJO));
    }

    @Override
    @Transactional
    public void completar(UserId actorId) {
        requireActorActivo(actorId);
        etapaOnboardingPort.marcarCompletada(actorId, FLUJO);
    }

    private AccionMapa aAccion(UserId actorId, AccionEntrada entrada) {
        return AccionMapa.crear(idGenerator.newId(), actorId, entrada.accionId(),
                AreaMapa.desdeClave(entrada.area()), entrada.texto(), entrada.frecuenciaSemanal(),
                aDias(entrada.dias()), MomentoAccion.desdeClave(entrada.momento()),
                EvidenciaAccion.desdeClave(entrada.evidencia()), clock);
    }

    private ProtocoloReemplazoMapa aProtocolo(UserId actorId, ProtocoloEntrada entrada) {
        return ProtocoloReemplazoMapa.crear(idGenerator.newId(), actorId, entrada.protocoloId(), entrada.patron(),
                entrada.disparador(), entrada.conductaActual(), entrada.respuestaAlternativa(), clock);
    }

    /** Enteros ISO 1..7. Un dia fuera de rango revienta aca y no llega a la base. */
    private static Set<DayOfWeek> aDias(List<Integer> dias) {
        if (dias == null || dias.isEmpty()) {
            return Set.of();
        }
        Set<DayOfWeek> resultado = new LinkedHashSet<>();
        for (Integer dia : dias) {
            if (dia == null || dia < 1 || dia > 7) {
                throw new IllegalArgumentException("Dia de la semana fuera de rango 1..7: " + dia);
            }
            resultado.add(DayOfWeek.of(dia));
        }
        return resultado;
    }

    /**
     * Mismo guard que el resto del modulo: el actor tiene que existir y no estar suspendido. Una
     * cuenta SUSPENDED con token valido recibe 403, no 200 (regla de pruebas de seguridad).
     */
    private void requireActorActivo(UserId actorId) {
        var actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Tu cuenta esta suspendida");
        }
    }
}
