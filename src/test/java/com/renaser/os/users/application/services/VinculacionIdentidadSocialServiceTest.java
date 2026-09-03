package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.IdentidadYaVinculadaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase.VincularIdentidadSocialCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Vinculacion explicita de una identidad social a una cuenta ya autenticada
 * (docs/MODULO_AUTH.md §6.9). Las tres pruebas que importan de verdad son las de seguridad: que
 * la identidad de otro usuario no se pueda robar, que un correo sin verificar no vincule, y que
 * una cuenta suspendida no llegue siquiera a canjear el codigo.
 */
@ExtendWith(MockitoExtension.class)
class VinculacionIdentidadSocialServiceTest {

    private static final String SUJETO = "google-sub-a-vincular";
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-01T12:00:00Z"));

    @Mock
    private VerificadorIdentidadProveedor verificadorGoogle;
    @Mock
    private LoadIdentidadExternaPort loadIdentidadExternaPort;
    @Mock
    private SaveIdentidadExternaPort saveIdentidadExternaPort;
    @Mock
    private LoadUserPort loadUserPort;

    private final UserId actorId = UserId.of(UUID.randomUUID());

    private VinculacionIdentidadSocialService service() {
        when(verificadorGoogle.proveedor()).thenReturn(ProveedorIdentidad.GOOGLE);
        return new VinculacionIdentidadSocialService(List.of(verificadorGoogle), loadIdentidadExternaPort,
                saveIdentidadExternaPort, new RequireActiveUserGuard(loadUserPort), RELOJ);
    }

    private VincularIdentidadSocialCommand comando() {
        return new VincularIdentidadSocialCommand(actorId, ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback");
    }

    private static User usuario(UserId id, UserStatus estado) {
        return User.rehydrate(id, new Email("dueña@renaser.dev"), UserRole.TRAINEE, estado, "Duena De La Cuenta",
                null, null, null, null);
    }

    private void actorActivo() {
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserStatus.ACTIVE)));
    }

    private void proveedorDevuelve(IdentidadVerificada identidad) {
        when(verificadorGoogle.verificar(any())).thenReturn(identidad);
    }

    @Test
    @DisplayName("identidad libre: se vincula al actor de la sesion")
    void identidadLibreSeVinculaAlActorDeLaSesion() {
        actorActivo();
        proveedorDevuelve(new IdentidadVerificada(SUJETO, "personal@gmail.com", true, "Duena"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO))
                .thenReturn(Optional.empty());

        service().vincular(comando());

        ArgumentCaptor<IdentidadExterna> guardada = ArgumentCaptor.forClass(IdentidadExterna.class);
        verify(saveIdentidadExternaPort).guardar(guardada.capture());
        assertThat(guardada.getValue().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(guardada.getValue().sujetoProveedor()).isEqualTo(SUJETO);
        assertThat(guardada.getValue().usuarioId()).isEqualTo(actorId);
        assertThat(guardada.getValue().vinculadaEn()).isEqualTo(RELOJ.now());
    }

    /**
     * El correo del proveedor NO tiene que coincidir con el de la cuenta (§6.9): vincular un
     * Google personal a una cuenta con correo de trabajo es legitimo y es lo que permiten Auth0 y
     * Clerk. Lo que impide que una misma cuenta de Google sirva a dos usuarios es la UNIQUE
     * (proveedor, sujeto_proveedor), no una comparacion de correos.
     */
    @Test
    @DisplayName("el correo del proveedor puede ser distinto al de la cuenta")
    void correoDistintoAlDeLaCuentaNoImpideVincular() {
        actorActivo();
        proveedorDevuelve(new IdentidadVerificada(SUJETO, "otro-correo-totalmente-distinto@gmail.com", true, "Duena"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO))
                .thenReturn(Optional.empty());

        service().vincular(comando());

        verify(saveIdentidadExternaPort).guardar(any());
    }

    /** El doble tap del cliente movil no puede ser un error, ni reescribir la fecha de vinculacion. */
    @Test
    @DisplayName("idempotente: si ya estaba vinculada a este mismo usuario, no vuelve a escribir")
    void identidadYaVinculadaAlMismoUsuarioEsIdempotente() {
        actorActivo();
        proveedorDevuelve(new IdentidadVerificada(SUJETO, "personal@gmail.com", true, "Duena"));
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO))
                .thenReturn(Optional.of(new IdentidadExterna(ProveedorIdentidad.GOOGLE, SUJETO, actorId,
                        "personal@gmail.com", Instant.parse("2026-08-01T00:00:00Z"))));

        service().vincular(comando());

        verify(saveIdentidadExternaPort, never()).guardar(any());
    }

    /**
     * La prueba mas importante del caso de uso: el vector de apropiacion en el sentido inverso al
     * de §6.4. Sin este chequeo, quien consiga un {@code code} de la cuenta social de otra persona
     * podria colgarla de su propio usuario.
     */
    @Test
    @DisplayName("la identidad de OTRO usuario no se puede robar: 409 y no se escribe nada")
    void identidadVinculadaAOtroUsuarioSeRechaza() {
        actorActivo();
        proveedorDevuelve(new IdentidadVerificada(SUJETO, "personal@gmail.com", true, "Duena"));
        UserId otroUsuario = UserId.of(UUID.randomUUID());
        when(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO))
                .thenReturn(Optional.of(new IdentidadExterna(ProveedorIdentidad.GOOGLE, SUJETO, otroUsuario,
                        "personal@gmail.com", Instant.parse("2026-08-01T00:00:00Z"))));

        VinculacionIdentidadSocialService service = service();
        assertThatThrownBy(() -> service.vincular(comando()))
                .isInstanceOf(IdentidadYaVinculadaException.class)
                // el mensaje no puede delatar de quien es la identidad: seria un oraculo
                .hasMessageNotContaining(otroUsuario.toString());

        verify(saveIdentidadExternaPort, never()).guardar(any());
    }

    @Test
    @DisplayName("email_verified=false no vincula")
    void emailSinVerificarNoVincula() {
        actorActivo();
        proveedorDevuelve(new IdentidadVerificada(SUJETO, "personal@gmail.com", false, "Duena"));

        VinculacionIdentidadSocialService service = service();
        assertThatThrownBy(() -> service.vincular(comando()))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);

        verifyNoInteractions(loadIdentidadExternaPort, saveIdentidadExternaPort);
    }

    @Test
    @DisplayName("si el proveedor rechaza el code, el error sube tal cual (401)")
    void proveedorQueRechazaElCodePropagaSuError() {
        actorActivo();
        when(verificadorGoogle.verificar(any()))
                .thenThrow(new IdentidadProveedorInvalidaException("GOOGLE"));

        VinculacionIdentidadSocialService service = service();
        assertThatThrownBy(() -> service.vincular(comando()))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);

        verifyNoInteractions(saveIdentidadExternaPort);
    }

    /**
     * Autorizacion negativa (CLAUDE.MD §0.3). Ademas del 403, se verifica que el proveedor
     * <b>ni siquiera se toque</b>: el {@code code} de OAuth es de un solo uso y quemarlo en una
     * peticion que igual iba a fallar obligaria a la persona a reiniciar el flujo del navegador.
     */
    @Test
    @DisplayName("cuenta suspendida: 403 y el code de OAuth no se quema")
    void cuentaSuspendidaNoVinculaNiCanjeaElCode() {
        when(verificadorGoogle.proveedor()).thenReturn(ProveedorIdentidad.GOOGLE);
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(usuario(actorId, UserStatus.SUSPENDED)));
        VinculacionIdentidadSocialService service = new VinculacionIdentidadSocialService(List.of(verificadorGoogle),
                loadIdentidadExternaPort, saveIdentidadExternaPort, new RequireActiveUserGuard(loadUserPort), RELOJ);

        assertThatThrownBy(() -> service.vincular(comando()))
                .isInstanceOf(NotAuthorizedException.class);

        verify(verificadorGoogle, never()).verificar(any());
        verifyNoInteractions(loadIdentidadExternaPort, saveIdentidadExternaPort);
    }
}
