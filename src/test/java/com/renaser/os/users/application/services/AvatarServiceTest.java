package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase.ConfirmarAvatarCommand;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase.SolicitarUrlAvatarCommand;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gap #4: avatar generico, mismo patron upload-url -> PUT -> confirmar del resto del sistema. */
@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private SaveUserPort saveUserPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;

    private AvatarService service;

    @BeforeEach
    void setUp() {
        service = new AvatarService(new RequireActiveUserGuard(loadUserPort), saveUserPort, almacenamientoPort);
    }

    private static User activo(UserId id) {
        return User.rehydrate(id, new Email("test" + id.value() + "@renaser.dev"), UserRole.TRAINEE,
                UserStatus.ACTIVE, "Test", null, null, null, null);
    }

    private static User suspendido(UserId id) {
        User user = activo(id);
        user.suspend();
        return user;
    }

    @Test
    @DisplayName("solicitarUrl pide una URL firmada con el bucket compartido y una ruta por usuario")
    void solicitarUrlDevuelveBucketYRutaEsperados() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://s3.example/avatares/" + actorId));

        var resultado = service.solicitarUrl(new SolicitarUrlAvatarCommand(actorId, "image/png"));

        assertThat(resultado.bucket()).isEqualTo(AvatarService.BUCKET_AVATARES);
        assertThat(resultado.ruta()).isEqualTo("avatares/" + actorId);
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede pedir URL de avatar")
    void solicitarUrlRechazaActorSuspendido() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId)));

        assertThatThrownBy(() -> service.solicitarUrl(new SolicitarUrlAvatarCommand(actorId, "image/png")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("confirmar resuelve una URL de lectura y la persiste como avatarUrl")
    void confirmarPersisteLaUrlResuelta() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        when(almacenamientoPort.firmarLectura(anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://s3.example/avatares/" + actorId + "?sig=abc"));

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(captor.capture());
        assertThat(captor.getValue().avatarUrl()).isEqualTo("https://s3.example/avatares/" + actorId + "?sig=abc");
    }

    @Test
    @DisplayName("BUG-3: un usuario SUSPENDIDO no puede confirmar su avatar")
    void confirmarRechazaActorSuspendido() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(suspendido(actorId)));

        assertThatThrownBy(() -> service.confirmar(
                new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES, "avatares/" + actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveUserPort, never()).save(any());
    }

    /** Self-only por diseño (CLAUDE.MD §5.3.3, adaptado): ninguno de los dos comandos tiene
     * un campo de "usuario objetivo" distinto del actor. */
    @Test
    @DisplayName("los comandos de avatar son self-only: solo llevan actorId, nunca un id de otro usuario")
    void comandosDeAvatarSonSelfOnly() {
        assertThat(SolicitarUrlAvatarCommand.class.getRecordComponents()).extracting("name")
                .containsExactly("actorId", "tipoContenido");
        assertThat(ConfirmarAvatarCommand.class.getRecordComponents()).extracting("name")
                .containsExactly("actorId", "bucket", "ruta");
    }
}
