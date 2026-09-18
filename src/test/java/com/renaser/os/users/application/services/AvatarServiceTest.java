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
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
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
    /** Avanzable: la version de la URL sale del instante, asi que dos confirmaciones necesitan
     * dos instantes distintos para parecerse a lo que pasa de verdad. */
    private MutableClock reloj;

    @BeforeEach
    void setUp() {
        reloj = new MutableClock(Instant.parse("2026-09-18T10:00:00Z"));
        service = new AvatarService(new RequireActiveUserGuard(loadUserPort), saveUserPort, almacenamientoPort, reloj);
    }

    /** Un reloj de prueba que se puede mover. `FixedClock` no sirve acá: con el instante congelado,
     * dos confirmaciones darian la misma version y la prueba pasaria por el motivo equivocado. */
    private static final class MutableClock implements com.renaser.os.shared.domain.Clock {
        private Instant ahora;

        private MutableClock(Instant ahora) {
            this.ahora = ahora;
        }

        @Override
        public Instant now() {
            return ahora;
        }

        /* La fecha del SERVIDOR, que el avatar no usa: la version sale del instante. Se implementa
           porque el puerto la exige, no porque haga falta acá (regla 02: el dia de una persona se
           deriva de su zona, nunca de esto). */
        @Override
        public java.time.LocalDate today() {
            return ahora.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }

        void avanzar(java.time.Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
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
        /* Corregido 2026-09-18: antes se exigia la URL EXACTA, sin ningun `?`. Desde que se agrega
           `?v=<instante>` para que el cambio de foto se vea (ver `confirmar`), la afirmacion que
           importa no es "sin query" sino "sin FIRMA": una firma vence y devuelve el defecto E-57;
           una version no vence nunca. */
        assertThat(guardado)
                .startsWith("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + actorId);
        assertThat(guardado).doesNotContain("X-Amz-Signature", "X-Amz-Expires");
        // Nunca se pide una firma de lectura: si se pidiera, lo guardado volveria a vencer.
        verify(almacenamientoPort, never()).firmarLectura(anyString(), any(Duration.class));
    }

    /**
     * <b>Corregido 2026-09-18.</b> Esta prueba decia lo contrario: que dos confirmaciones dieran
     * EXACTAMENTE la misma URL, y lo justificaba con que asi el cliente puede cachearla —un muro
     * con 20 avatares no vuelve a descargar 20 fotos cada vez—. Lo segundo sigue siendo cierto y
     * hay que conservarlo. Lo primero era el bug: confirmar no es leer, es <b>subir una foto
     * nueva</b>, y devolver la misma URL de siempre garantizaba que ningun cache se enterara. La
     * foto se subia bien y la persona seguia viendo la vieja.
     *
     * <p>Las dos cosas conviven porque la version cambia SOLO al confirmar. Entre dos cambios de
     * foto, el {@code avatarUrl} guardado es una constante que se lee de la base: los 20 avatares
     * del muro se siguen cacheando igual. Lo unico que se pierde es el cache de la foto que la
     * persona acaba de reemplazar, que es exactamente lo que hay que perder.
     */
    @Test
    @DisplayName("Cambiar de foto cambia la URL, para que el cache no muestre la vieja")
    void dosConfirmacionesDanUrlsDistintas() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        AtomicInteger invocaciones = new AtomicInteger();
        when(almacenamientoPort.urlPublica(anyString())).thenAnswer(inv -> {
            invocaciones.incrementAndGet();
            return URI.create("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/" + inv.getArgument(0));
        });

        /* Se anota la URL EN EL MOMENTO de guardar, y no con un `ArgumentCaptor`. `User` es un
           agregado mutable y el servicio guarda dos veces el MISMO objeto: el captor conserva dos
           referencias a esa unica instancia, asi que al final las dos muestran el ultimo valor y
           la prueba pasaria diga lo que diga el codigo. */
        List<String> guardadas = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            guardadas.add(inv.getArgument(0, User.class).avatarUrl());
            return null;
        }).when(saveUserPort).save(org.mockito.ArgumentMatchers.any(User.class));

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));
        reloj.avanzar(java.time.Duration.ofSeconds(30));
        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));

        assertThat(guardadas).hasSize(2);
        String primera = guardadas.get(0);
        String segunda = guardadas.get(1);
        assertThat(primera).isNotEqualTo(segunda);
        // El objeto es el mismo: lo unico que cambia es la version, no la ruta.
        assertThat(primera).startsWith("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + actorId);
        assertThat(segunda).startsWith("https://s3-renaser90dias.s3.us-east-1.amazonaws.com/avatares/" + actorId);
        // Y lo que E-57 protegia sigue en pie: nada de esto es una firma que vence.
        assertThat(primera).doesNotContain("X-Amz-Signature", "X-Amz-Expires");
        assertThat(segunda).doesNotContain("X-Amz-Signature", "X-Amz-Expires");
        assertThat(invocaciones).hasValue(2);
    }

    /**
     * El separador se elige mirando la URL: un adaptador que ya devuelva una consulta no puede
     * terminar con dos `?`. Hoy S3 no la trae, pero `urlPublica` es un puerto y el proximo
     * adaptador —un CDN con parametros— no deberia romper el avatar de todos en silencio.
     */
    @Test
    @DisplayName("Si la URL publica ya trae consulta, la version se agrega con & y no con otro ?")
    void respetaLaConsultaQueYaTraeLaUrl() {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(actorId)).thenReturn(Optional.of(activo(actorId)));
        when(almacenamientoPort.urlPublica(anyString()))
                .thenAnswer(inv -> URI.create("https://cdn.renaser.test/" + inv.getArgument(0) + "?tenant=renaser"));

        service.confirmar(new ConfirmarAvatarCommand(actorId, AvatarService.BUCKET_AVATARES,
                "avatares/" + actorId));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(captor.capture());
        String guardado = captor.getValue().avatarUrl();
        assertThat(guardado).contains("?tenant=renaser&v=");
        assertThat(guardado.chars().filter(ch -> ch == '?').count()).isEqualTo(1);
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
        /* Corregido 2026-09-18: decia `endsWith`. Desde que la URL lleva `?v=<instante>` lo que hay
           que afirmar es que la RUTA es la del actor, no que la cadena termine ahi. */
        assertThat(captor.getValue().avatarUrl()).contains("/avatares/" + actorId + "?v=");
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
