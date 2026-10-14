package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.CambiarProbadorasUseCase.ProbadoraCommand;
import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueCambiado;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sumar y quitar probadoras (contrato fase 2 §3.4): 404, 409, la fila de la auditoría y que no avisa por push. */
class ProbadorasDeDespliegueServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T03:30:00Z"));
    private static final String SESION = "sesion-1";

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final Dobles.Despliegues despliegues = new Dobles.Despliegues();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private ProbadorasDeDespliegueService service;
    private String consola;

    @BeforeEach
    void armar() {
        UserSummary dueno = new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        UserSummary deAna = new UserSummary(ana, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(usuarios.findById(soporte)).thenReturn(Optional.of(dueno));
        when(usuarios.findById(ana)).thenReturn(Optional.of(deAna));
        when(usuarios.findByIds(anyCollection())).thenReturn(Map.of(soporte, dueno, ana, deAna));
        MetricasDeDesplieguePort metricas = mock(MetricasDeDesplieguePort.class);
        when(metricas.desde(any(), any())).thenReturn(List.of());
        CorreosDeUsuariosFinder correos = mock(CorreosDeUsuariosFinder.class);
        when(correos.correosDe(anyCollection())).thenReturn(Map.of(ana, "ana@renaser.test"));
        service = new ProbadorasDeDespliegueService(new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15)),
                ConsolaDeDesplieguesServiceTest.estadoVigente(), despliegues, despliegues, usuarios,
                new AnotadorDeDespliegues(auditoria, events, UUID::randomUUID),
                new DesplieguesEnConsola(despliegues, metricas, usuarios, correos, CLOCK), CLOCK);
        consola = tokens.emitirDeConsola(soporte, SESION, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("sumar a una persona que no existe: 404; a una funcionalidad sin despliegue: 409")
    void personaInexistenteYSinDespliegue() {
        assertThatThrownBy(() -> service.sumar(pedido(UserId.of(UUID.randomUUID()), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.sumar(pedido(ana, null))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está en despliegue");
    }

    @Test
    @DisplayName("sumar: la ve, la auditoría dice 0 → 1 probadores y a quién; no avisa por push; repetirlo no hace nada")
    void sumar() {
        enDespliegue();

        DespliegueEnConsola vista = service.sumar(pedido(ana, "Le toca probar"));
        service.sumar(pedido(ana, null));

        assertThat(vista.probadores()).singleElement().satisfies(probadora -> {
            assertThat(probadora.nombre()).isEqualTo("Ana Pérez");
            assertThat(probadora.email()).isEqualTo("ana@renaser.test");
        });
        assertThat(despliegues.filas.get("eventos").incluye(ana)).isTrue();
        CambioAuditado fila = auditoria.filas.getFirst();
        assertThat(auditoria.filas).hasSize(1);
        assertThat(fila.antes()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 0, "probadores", 0)));
        assertThat(fila.despues()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 0, "probadores", 1)));
        assertThat(fila.motivo()).isEqualTo("Probadora sumada: Ana Pérez (" + ana + "). Motivo: Le toca probar");
        verify(events, times(1)).publishEvent(any(DespliegueCambiado.class));
        verify(events, never()).publishEvent(any(FuncionalidadCambiadaEvent.class));
    }

    @Test
    @DisplayName("quitar: audita 1 → 0; quitar a quien no era probadora (o sin despliegue) no hace nada")
    void quitar() {
        enDespliegue();
        service.sumar(pedido(ana, null));

        service.quitar(pedido(ana, null));
        service.quitar(pedido(ana, null));
        service.quitar(new ProbadoraCommand(acceso(consola), "radar", ana, null, null));

        assertThat(despliegues.filas.get("eventos").probadores()).isEmpty();
        assertThat(auditoria.filas).hasSize(2);
        assertThat(auditoria.filas.get(1).despues()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 0,
                "probadores", 0)));
        assertThat(auditoria.filas.get(1).motivo()).startsWith("Probadora quitada: Ana Pérez");
    }

    @Test
    @DisplayName("solo Soporte TI con la consola abierta: Ana (aprendiz) 403, sin token 423")
    void cerrojos() {
        enDespliegue();
        assertThatThrownBy(() -> service.sumar(new ProbadoraCommand(new AccesoDeConsola(ana, consola, SESION),
                "eventos", ana, null, null))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.quitar(new ProbadoraCommand(acceso(null), "eventos", ana, null, null)))
                .isInstanceOf(ConsolaBloqueadaException.class);
    }

    private void enDespliegue() {
        despliegues.empezar(DespliegueDeFuncionalidad.empezar("eventos", 0, 1, soporte, CLOCK.now()));
    }

    private ProbadoraCommand pedido(UserId persona, String motivo) {
        return new ProbadoraCommand(acceso(consola), "eventos", persona, motivo, "Prueba/1.0");
    }

    private AccesoDeConsola acceso(String token) {
        return new AccesoDeConsola(soporte, token, SESION);
    }
}
