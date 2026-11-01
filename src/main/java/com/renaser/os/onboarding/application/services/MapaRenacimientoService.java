package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.mapa.CompletarEtapaMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.ConsultarMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.MapaRetiradoException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * El Mapa de Renacimiento (Dia 7), <b>retirado</b> por la spec 026 (D-303, pedido del dueño del 2026-10-31: «el Mapa ya no va, será reemplazado con la nueva ficha»).
 *
 * <p>Lo que hay hoy: <b>se puede leer</b> lo que cada persona alcanzó a guardar (nada se borra: los datos quedan por si el dueño quiere recuperar algo), y <b>no se
 * puede escribir</b> —guardar las listas ni terminar la etapa responden 409 {@code MAPA_RETIRADO}—, así que quien lo tenía a medias no lo continúa.
 *
 * <p>{@code stageCompleted} sale {@code true} para todos a propósito: la app publicada antes de este cambio decide con él si muestra la puerta del Mapa en
 * Plan («Primero tu Mapa de Renacimiento») y su tarjeta en Hoy. Con {@code true} esa app deja de ofrecerlo y nadie queda bloqueado (FR-025). Es una marca de
 * compatibilidad, no un dato de la persona.
 *
 * <p>Ya no se guarda el flujo {@code mapa_dia7} por {@code RespuestaService} tampoco (ver su guarda de flujos reservados).
 */
@Service
public class MapaRenacimientoService
        implements GuardarPasoMapaUseCase, ConsultarMapaUseCase, CompletarEtapaMapaUseCase {

    /** El flujo del motor de onboarding en el que vive el Mapa (V41). */
    public static final String FLUJO = "mapa_dia7";

    private final ConsultarActorPort actorPort;
    private final LoadMapaPort loadMapaPort;

    public MapaRenacimientoService(ConsultarActorPort actorPort, LoadMapaPort loadMapaPort) {
        this.actorPort = actorPort;
        this.loadMapaPort = loadMapaPort;
    }

    @Override
    public void guardarSistemaDeEjecucion(GuardarAccionesCommand command) {
        requireActorActivo(command.actorId());
        throw new MapaRetiradoException();
    }

    @Override
    public void guardarProtocolosDeReemplazo(GuardarProtocolosCommand command) {
        requireActorActivo(command.actorId());
        throw new MapaRetiradoException();
    }

    @Override
    @Transactional(readOnly = true)
    public MapaDelParticipante consultar(UserId actorId) {
        requireActorActivo(actorId);
        return new MapaDelParticipante(loadMapaPort.accionesDe(actorId), loadMapaPort.protocolosDe(actorId), true);
    }

    @Override
    public void completar(UserId actorId) {
        requireActorActivo(actorId);
        throw new MapaRetiradoException();
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
