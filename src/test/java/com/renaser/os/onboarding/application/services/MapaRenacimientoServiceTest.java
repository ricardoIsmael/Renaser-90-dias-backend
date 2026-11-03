package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.AccionEntrada;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.GuardarAccionesCommand;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.GuardarProtocolosCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.MapaRetiradoException;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Spec 026 (D-303): el Mapa de Renacimiento se retiro. Se puede LEER lo que cada persona alcanzo a guardar, no se puede escribir, y
 * {@code etapaCompletada} sale siempre {@code true} para que la app publicada antes no bloquee a nadie.
 */
@ExtendWith(MockitoExtension.class)
class MapaRenacimientoServiceTest {

    @Mock private ConsultarActorPort actorPort;
    @Mock private LoadMapaPort loadMapaPort;

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final MapaRenacimientoService service() {
        return new MapaRenacimientoService(actorPort, loadMapaPort);
    }

    private void actorActivo() {
        when(actorPort.deActor(actor)).thenReturn(Optional.of(new ActorOnboarding(actor, false)));
    }

    private static AccionEntrada entrada() {
        return new AccionEntrada("a1", "salud", "Caminar 40 minutos", 3, List.of(1, 3, 5), "manana", "foto");
    }

    @Test
    void guardarElSistemaDeEjecucionYaNoSePuede() {
        actorActivo();

        assertThatThrownBy(() -> service().guardarSistemaDeEjecucion(new GuardarAccionesCommand(actor, List.of(entrada()))))
                .isInstanceOf(MapaRetiradoException.class);
    }

    @Test
    void guardarLosProtocolosYaNoSePuede() {
        actorActivo();

        assertThatThrownBy(() -> service().guardarProtocolosDeReemplazo(new GuardarProtocolosCommand(actor, List.of())))
                .isInstanceOf(MapaRetiradoException.class);
    }

    @Test
    void terminarLaEtapaYaNoSePuede() {
        actorActivo();

        assertThatThrownBy(() -> service().completar(actor)).isInstanceOf(MapaRetiradoException.class);
    }

    @Test
    void leerDevuelveLoGuardadoYLaEtapaSiempreCompletaParaLaAppVieja() {
        actorActivo();
        when(loadMapaPort.accionesDe(actor)).thenReturn(AccionesDelMapa.vacio());
        when(loadMapaPort.protocolosDe(actor)).thenReturn(ProtocolosDelMapa.vacio());

        var mapa = service().consultar(actor);

        assertThat(mapa.etapaCompletada()).isTrue();
        assertThat(mapa.acciones().acciones()).isEmpty();
    }

    @Test
    void unaCuentaSuspendidaNoPuedeLeerNiEscribirYUnaInexistenteNoEsta() {
        when(actorPort.deActor(actor)).thenReturn(Optional.of(new ActorOnboarding(actor, true)));

        assertThatThrownBy(() -> service().consultar(actor)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service().completar(actor)).isInstanceOf(NotAuthorizedException.class);

        when(actorPort.deActor(actor)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().consultar(actor)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void elFlujoDelMapaSigueSiendoElDeSiempre() {
        assertThat(MapaRenacimientoService.FLUJO).isEqualTo("mapa_dia7");
    }
}
