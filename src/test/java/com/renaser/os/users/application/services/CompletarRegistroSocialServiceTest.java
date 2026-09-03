package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RegistroPendienteSocialInvalidoException;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase.CompletarRegistroSocialCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Segundo paso del alta social (docs/MODULO_AUTH.md §6.10, D-65). El foco de estas pruebas es la
 * regla de seguridad central: el correo y el sujeto del proveedor SIEMPRE salen del registro que
 * devuelve {@link TokenRegistroPendienteSocialPort#consumir}, nunca del comando.
 */
@ExtendWith(MockitoExtension.class)
class CompletarRegistroSocialServiceTest {

    @Mock
    private TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;
    @Mock
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;
    @Mock
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Mock
    private LoadAccountRequestPort loadAccountRequestPort;

    private CompletarRegistroSocialService service() {
        return new CompletarRegistroSocialService(tokenRegistroPendienteSocialPort, submitAccountRequestUseCase,
                tokenVerificacionEmailPort, loadAccountRequestPort);
    }

    private static CompletarRegistroSocialCommand command(String token, String fullName) {
        return new CompletarRegistroSocialCommand(token, fullName, "+54 341 1234567", "Rosario", "127.0.0.1");
    }

    @Test
    void tokenValidoAbreLaAccountRequestConElCorreoDelRegistroGuardado() {
        RegistroPendienteSocial registro = new RegistroPendienteSocial(ProveedorIdentidad.GOOGLE,
                "google-sub-completar", "delregistro@renaser.dev", "Persona Nueva");
        when(tokenRegistroPendienteSocialPort.consumir("token-valido")).thenReturn(Optional.of(registro));
        when(tokenVerificacionEmailPort.generar(eq("delregistro@renaser.dev"), any()))
                .thenReturn("token-verificacion");
        AccountRequestId solicitudId = AccountRequestId.of(UUID.randomUUID());
        when(submitAccountRequestUseCase.submit(any())).thenReturn(solicitudId);

        AccountRequestId resultado = service().completar(command("token-valido", "Nombre Confirmado"));

        assertThat(resultado).isEqualTo(solicitudId);
        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("delregistro@renaser.dev");
        assertThat(captor.getValue().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(captor.getValue().sujetoProveedor()).isEqualTo("google-sub-completar");
        assertThat(captor.getValue().verificationToken()).isEqualTo("token-verificacion");
        assertThat(captor.getValue().contrasena()).isNull();
    }

    /**
     * La prueba central de seguridad de D-65: el {@code fullName} SI viaja del comando (la
     * persona puede corregirlo), pero el correo NUNCA — aunque el comando no tenga ni siquiera
     * un campo para mandarlo, esta prueba confirma que lo que llega a
     * {@code SubmitAccountRequestUseCase} es el email del registro, no otra cosa.
     */
    @Test
    void elFullNameDelComandoReemplazaAlDelProveedorPeroElCorreoSiempreEsElDelRegistro() {
        RegistroPendienteSocial registro = new RegistroPendienteSocial(ProveedorIdentidad.GOOGLE,
                "google-sub-nombre", "original@renaser.dev", "Nombre Del Proveedor");
        when(tokenRegistroPendienteSocialPort.consumir("token-nombre")).thenReturn(Optional.of(registro));
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-verificacion-2");
        when(submitAccountRequestUseCase.submit(any())).thenReturn(AccountRequestId.of(UUID.randomUUID()));

        service().completar(command("token-nombre", "Nombre Corregido Por La Persona"));

        ArgumentCaptor<SubmitAccountRequestCommand> captor = ArgumentCaptor.forClass(SubmitAccountRequestCommand.class);
        verify(submitAccountRequestUseCase).submit(captor.capture());
        assertThat(captor.getValue().fullName()).isEqualTo("Nombre Corregido Por La Persona");
        assertThat(captor.getValue().email()).isEqualTo("original@renaser.dev");
    }

    @Test
    void tokenInexistenteSeRechazaSinLlegarASubmit() {
        when(tokenRegistroPendienteSocialPort.consumir("token-que-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completar(command("token-que-no-existe", "Alguien")))
                .isInstanceOf(RegistroPendienteSocialInvalidoException.class);
        verify(submitAccountRequestUseCase, never()).submit(any());
        verify(tokenVerificacionEmailPort, never()).generar(any(), any());
    }

    @Test
    void tokenYaConsumidoSeRechaza() {
        // consumir() es GETDEL: la segunda vez que se llama con el mismo token, el puerto ya
        // devuelve vacio — el mock lo simula sin necesitar Redis real (eso lo cubre el test de
        // integracion del adaptador).
        when(tokenRegistroPendienteSocialPort.consumir("token-ya-usado")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completar(command("token-ya-usado", "Alguien")))
                .isInstanceOf(RegistroPendienteSocialInvalidoException.class);
    }

    // ─── C-17: dos confirmaciones concurrentes de la misma identidad social ────────────

    private static AccountRequest solicitudPendienteDe(OrigenSocial origen) {
        return AccountRequest.submit(AccountRequestId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                new Email("carrera@renaser.dev"), "Carrera Social", null, null, "127.0.0.1", origen,
                FixedClock.at(Instant.parse("2026-09-01T10:00:00Z")));
    }

    @Test
    @DisplayName("C-17: si dos confirmaciones de la MISMA identidad social chocan contra el UNIQUE "
            + "de la base, la que pierde la carrera devuelve la solicitud que ya gano, no el 409 generico")
    void dosConfirmacionesConcurrentesDeLaMismaIdentidadDevuelvenLaSolicitudGanadora() {
        RegistroPendienteSocial registro = new RegistroPendienteSocial(ProveedorIdentidad.GOOGLE,
                "google-sub-carrera", "carrera@renaser.dev", "Carrera Social");
        when(tokenRegistroPendienteSocialPort.consumir("token-carrera")).thenReturn(Optional.of(registro));
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-verificacion-carrera");
        when(submitAccountRequestUseCase.submit(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        OrigenSocial origen = new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-carrera");
        AccountRequest solicitudGanadora = solicitudPendienteDe(origen);
        when(loadAccountRequestPort.porOrigenSocial(origen)).thenReturn(Optional.of(solicitudGanadora));

        AccountRequestId resultado = service().completar(command("token-carrera", "Carrera Social"));

        assertThat(resultado).isEqualTo(solicitudGanadora.id());
    }

    @Test
    @DisplayName("C-17: si el UNIQUE choca pero NO hay una solicitud pendiente de la MISMA identidad "
            + "social (otro tipo de conflicto), se relanza el error original en vez de inventar una respuesta")
    void unConflictoDeUniqueSinSolicitudDeLaMismaIdentidadRelanzaElErrorOriginal() {
        RegistroPendienteSocial registro = new RegistroPendienteSocial(ProveedorIdentidad.GOOGLE,
                "google-sub-otro-conflicto", "otro-conflicto@renaser.dev", "Otra Persona");
        when(tokenRegistroPendienteSocialPort.consumir("token-otro-conflicto")).thenReturn(Optional.of(registro));
        when(tokenVerificacionEmailPort.generar(any(), any())).thenReturn("token-verificacion-otro");
        DataIntegrityViolationException fallo = new DataIntegrityViolationException("duplicate key");
        when(submitAccountRequestUseCase.submit(any())).thenThrow(fallo);
        when(loadAccountRequestPort.porOrigenSocial(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completar(command("token-otro-conflicto", "Otra Persona")))
                .isSameAs(fallo);
    }
}
