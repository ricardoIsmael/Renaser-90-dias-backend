package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase.ResumenHome;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeAgregadoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private ConsultarPuntajeUseCase consultarPuntajeUseCase;

    private HomeAgregadoService service;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("consultar() proyecta puntaje/coherencia/racha del propio actor")
    void consultarProyectaElPropioPuntaje() {
        service = new HomeAgregadoService(consultarPuntajeUseCase);
        PuntajeParticipante puntaje = PuntajeParticipante.rehydrate(actor, new BigDecimal("87.50"), 150, 4, 9,
                CLOCK.now());
        when(consultarPuntajeUseCase.consultar(actor, actor)).thenReturn(puntaje);

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.puntosLiga()).isEqualTo(150);
        assertThat(resumen.coherencia()).isEqualByComparingTo("87.50");
        assertThat(resumen.rachaActual()).isEqualTo(4);
        assertThat(resumen.rachaMaxima()).isEqualTo(9);
    }

    @Test
    @DisplayName("los 3 huecos sin finder publico se documentan, nunca se inventan")
    void documentaLosBloqueosSinInventarDatos() {
        service = new HomeAgregadoService(consultarPuntajeUseCase);
        when(consultarPuntajeUseCase.consultar(actor, actor))
                .thenReturn(PuntajeParticipante.inicial(actor, CLOCK));

        ResumenHome resumen = service.consultar(actor);

        assertThat(resumen.bloqueos()).hasSize(3);
        assertThat(resumen.bloqueos()).anySatisfy(b -> assertThat(b).contains("habitosHoy"));
        assertThat(resumen.bloqueos()).anySatisfy(b -> assertThat(b).contains("proximoEventoCalendario"));
        assertThat(resumen.bloqueos()).anySatisfy(b -> assertThat(b).contains("notificacionesNoLeidas"));
    }

    @Test
    @DisplayName("un actor suspendido no ve su resumen de Inicio (mismo guard que ConsultarPuntajeUseCase)")
    void actorSuspendidoEsRechazado() {
        service = new HomeAgregadoService(consultarPuntajeUseCase);
        when(consultarPuntajeUseCase.consultar(actor, actor))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }
}
