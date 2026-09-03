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
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Se comporta como el adaptador de S3: la URL publica es siempre la misma para una ruta. */
    private void stubUrlPublicaDeterminista() {
        when(almacenamientoPort.urlPublica(anyString()))
                .thenAnswer(inv -> URI.create("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/"
                        + inv.getArgument(0)));
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

    /**
     * E-57: lo que se persiste es la URL PERMANENTE. Antes se guardaba la URL de lectura
     * prefirmada (7 dias) y a la semana el avatar quedaba roto para siempre, en todas las
     * pantallas que lo muestran.
     */
    @Test
    @DisplayName("E-57: confirmar persiste una URL permanente, sin firma ni vencimiento")
    void confirmarPersisteUnaUrlPermanente() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        stubUrlPublicaDeterminista();

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(captor.capture());
        String guardado = captor.getValue().avatarUrl();
        assertThat(guardado)
                .isEqualTo("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + actorId);
        assertThat(guardado).doesNotContain("?", "X-Amz-Signature", "X-Amz-Expires");
        // Nunca se pide una firma de lectura: si se pidiera, lo guardado volveria a vencer.
        verify(almacenamientoPort, never()).firmarLectura(anyString(), any(Duration.class));
    }

    /**
     * La prueba de que el defecto murio, y de la razon por la que se eligio URL publica en vez de
     * firmar al leer: dos lecturas del mismo avatar dan EXACTAMENTE la misma URL. Eso es lo que
     * permite que el cliente la cachee — una prefirmada cambiaria en cada respuesta y un muro con
     * 20 avatares volveria a descargar las 20 fotos cada vez.
     */
    @Test
    @DisplayName("E-57: dos lecturas del mismo avatar devuelven la misma URL, sin query de firma")
    void dosLecturasDelMismoAvatarDevuelvenLaMismaUrl() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        // Cuenta cuantas veces se llamo: la URL publica no depende de la invocacion, la prefirmada si.
        AtomicInteger invocaciones = new AtomicInteger();
        when(almacenamientoPort.urlPublica(anyString())).thenAnswer(inv -> {
            invocaciones.incrementAndGet();
            return URI.create("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/" + inv.getArgument(0));
        });

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));
        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort, org.mockito.Mockito.times(2)).save(captor.capture());
        String primera = captor.getAllValues().get(0).avatarUrl();
        String segunda = captor.getAllValues().get(1).avatarUrl();
        assertThat(primera).isEqualTo(segunda);
        assertThat(URI.create(primera).getQuery()).isNull();
        assertThat(invocaciones).hasValue(2);
    }

    /**
     * La `ruta` viaja en el body y no se confia en ella: la ruta publicada la recalcula el
     * servicio desde el actor, asi que pedir que se publique el objeto de otro no cambia nada.
     */
    @Test
    @DisplayName("confirmar ignora la ruta del body y publica siempre la del propio actor")
    void confirmarIgnoraLaRutaDelBody() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        stubUrlPublicaDeterminista();

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "contratos/" + UUID.randomUUID() + "/firma.png"));

        verify(almacenamientoPort).urlPublica("avatares/" + actorId);
        var captor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(captor.capture());
        assertThat(captor.getValue().avatarUrl()).endsWith("/avatares/" + actorId);
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
