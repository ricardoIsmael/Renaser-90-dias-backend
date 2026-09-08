package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.AccionEntrada;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.GuardarAccionesCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.mapa.ReemplazarListaMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapaRenacimientoServiceTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-08T02:00:00Z"));

    @Mock private ConsultarActorPort actorPort;
    @Mock private LoadMapaPort loadMapaPort;
    @Mock private ReemplazarListaMapaPort reemplazarListaMapaPort;
    @Mock private EtapaOnboardingPort etapaOnboardingPort;

    private MapaRenacimientoService service;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        IdGenerator idGenerator = UUID::randomUUID;
        service = new MapaRenacimientoService(actorPort, loadMapaPort, reemplazarListaMapaPort,
                etapaOnboardingPort, idGenerator, RELOJ);
    }

    private void actorActivo() {
        when(actorPort.deActor(actor)).thenReturn(Optional.of(new ActorOnboarding(actor, false)));
    }

    private static AccionEntrada entrada(String id, String area) {
        return new AccionEntrada(id, area, "Caminar 40 minutos", 3, List.of(1, 3, 5), "manana", "foto");
    }

    @Test
    void guardarElPasoReemplazaElSistemaDeEjecucionCompleto() {
        actorActivo();

        service.guardarSistemaDeEjecucion(new GuardarAccionesCommand(actor,
                List.of(entrada("a1", "salud"), entrada("a2", "relaciones"))));

        var capturadas = ArgumentCaptor.forClass(AccionesDelMapa.class);
        verify(reemplazarListaMapaPort).reemplazarAcciones(org.mockito.ArgumentMatchers.eq(actor),
                capturadas.capture());
        assertThat(capturadas.getValue().acciones()).hasSize(2);
        assertThat(capturadas.getValue().acciones().getFirst().dias())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    /**
     * El techo del dominio tiene que cortar ANTES de tocar la base: si el rechazo llegara despues
     * del `delete`, un PUT invalido dejaria al aprendiz sin sistema de ejecucion.
     */
    @Test
    void unaSeptimaAccionSeRechazaSinEscribirNada() {
        actorActivo();
        var siete = List.of(entrada("a1", "salud"), entrada("a2", "salud"), entrada("a3", "negocio_dinero"),
                entrada("a4", "negocio_dinero"), entrada("a5", "relaciones"), entrada("a6", "relaciones"),
                entrada("a7", "salud"));

        assertThatThrownBy(() -> service.guardarSistemaDeEjecucion(new GuardarAccionesCommand(actor, siete)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reemplazarListaMapaPort, never()).reemplazarAcciones(any(), any());
    }

    @Test
    void unDiaFueraDeRangoSeRechaza() {
        actorActivo();
        var conDiaOcho = List.of(new AccionEntrada("a1", "salud", "Caminar 40 minutos", 3, List.of(8), null, null));

        assertThatThrownBy(() -> service.guardarSistemaDeEjecucion(new GuardarAccionesCommand(actor, conDiaOcho)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..7");
    }

    @Test
    void unaCuentaSuspendidaNoPuedeGuardarNiLeerSuMapa() {
        when(actorPort.deActor(actor)).thenReturn(Optional.of(new ActorOnboarding(actor, true)));

        assertThatThrownBy(() -> service.guardarSistemaDeEjecucion(
                new GuardarAccionesCommand(actor, List.of(entrada("a1", "salud")))))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
        verify(reemplazarListaMapaPort, never()).reemplazarAcciones(any(), any());
    }

    @Test
    void completarMarcaLaEtapaDelMapaYNoOtra() {
        actorActivo();

        service.completar(actor);

        verify(etapaOnboardingPort).marcarCompletada(actor, MapaRenacimientoService.FLUJO);
        assertThat(MapaRenacimientoService.FLUJO).isEqualTo("mapa_dia7");
    }
}
