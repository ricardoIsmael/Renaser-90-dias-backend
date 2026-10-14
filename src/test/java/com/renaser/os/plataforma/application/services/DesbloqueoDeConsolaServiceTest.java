package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.DesbloquearConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.Desbloqueo;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.Para;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.ContrasenaIncorrectaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConfirmacionDeContrasena;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El desbloqueo de la consola (plan.md D6). Sin Spring. */
class DesbloqueoDeConsolaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-13T03:30:00Z"));

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ConfirmacionDeContrasena contrasenas = mock(ConfirmacionDeContrasena.class);
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private final Dobles.Limitador limitador = new Dobles.Limitador();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private DesbloqueoDeConsolaService service;

    @BeforeEach
    void armar() {
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        when(contrasenas.esSuContrasena(soporte, "buena")).thenReturn(true);
        service = new DesbloqueoDeConsolaService(new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15)),
                contrasenas, tokens, limitador, auditoria, UUID::randomUUID, CLOCK, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("con la contraseña buena: token de consola por 15 minutos y una fila DESBLOQUEO «OK»")
    void desbloquea() {
        Desbloqueo desbloqueo = service.desbloquear(comando("buena", Para.CONSOLA, null));

        assertThat(desbloqueo.venceEn()).isEqualTo(CLOCK.now().plus(Duration.ofMinutes(15)));
        assertThat(tokens.consola).containsKey(desbloqueo.token());
        assertThat(auditoria.filas).singleElement().satisfies(fila -> {
            assertThat(fila.tipo()).isEqualTo(TipoDeCambio.DESBLOQUEO);
            assertThat(fila.despues()).containsEntry("resultado", "OK");
        });
    }

    @Test
    @DisplayName("con la contraseña mal: 401 CONTRASENA_INCORRECTA, cuenta el fallo y lo audita")
    void contrasenaMal() {
        assertThatThrownBy(() -> service.desbloquear(comando("mala", Para.CONSOLA, null)))
                .isInstanceOf(ContrasenaIncorrectaException.class);

        assertThat(limitador.fallos).isEqualTo(1);
        assertThat(auditoria.filas).singleElement()
                .satisfies(fila -> assertThat(fila.despues()).containsEntry("resultado", "CONTRASENA_INCORRECTA"));
        assertThat(tokens.consola).isEmpty();
    }

    @Test
    @DisplayName("con cinco fallos en la ventana: 429 sin mirar la contraseña")
    void bloqueadoPorIntentos() {
        limitador.fallos = DesbloqueoDeConsolaService.FALLOS_MAXIMOS;

        assertThatThrownBy(() -> service.desbloquear(comando("buena", Para.CONSOLA, null)))
                .isInstanceOf(RateLimitExceededException.class);
        verify(contrasenas, never()).esSuContrasena(any(), anyString());
    }

    @Test
    @DisplayName("un ADMIN no desbloquea la consola, ni con su contraseña: 403")
    void adminNoDesbloquea() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(usuarios.findById(admin)).thenReturn(Optional.of(
                new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.desbloquear(new DesbloquearConsolaCommand(
                new AccesoDeConsola(admin, null, "s"), "buena", Para.CONSOLA, null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(auditoria.filas).isEmpty();
    }

    @Test
    @DisplayName("la reautenticación pide la consola abierta; con ella, un token de 5 minutos")
    void reautenticacion() {
        assertThatThrownBy(() -> service.desbloquear(comando("buena", Para.CAMBIO_CRITICO, null)))
                .isInstanceOf(ConsolaBloqueadaException.class);

        String consola = tokens.emitirDeConsola(soporte, "s", Duration.ofMinutes(15));
        Desbloqueo reautenticacion = service.desbloquear(comando("buena", Para.CAMBIO_CRITICO, consola));

        assertThat(reautenticacion.venceEn()).isEqualTo(CLOCK.now().plus(Duration.ofMinutes(5)));
        assertThat(tokens.reautenticacion).containsKey(reautenticacion.token());
        assertThat(auditoria.filas).last().satisfies(fila ->
                assertThat(fila.tipo()).isEqualTo(TipoDeCambio.REAUTENTICACION));
    }

    private DesbloquearConsolaCommand comando(String contrasena, Para para, String tokenDeConsola) {
        return new DesbloquearConsolaCommand(new AccesoDeConsola(soporte, tokenDeConsola, "s"), contrasena, para,
                "JUnit");
    }
}
