package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase.EditarComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase.EscribirComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase.OcultarComentarioCommand;
import com.renaser.os.community.application.ports.out.publicacion.LoadComentarioPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.SaveComentarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoPublicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComentarioMuroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadPublicacionPort loadPublicacionPort;
    @Mock
    private LoadComentarioPort loadComentarioPort;
    @Mock
    private SaveComentarioPort saveComentarioPort;
    @Mock
    private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private ComentarioMuroService service;

    private final UserId autor = UserId.of(UUID.randomUUID());
    private final UserId otro = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ComentarioMuroService(loadPublicacionPort, loadComentarioPort, saveComentarioPort,
                consultarPerfilUsuarioPort, userSummaryFinder, CLOCK);
        lenient().when(consultarPerfilUsuarioPort.porId(any())).thenReturn(Optional.empty());
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(otro))
                .thenReturn(Optional.of(new UserSummary(otro, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(autor))
                .thenReturn(Optional.of(new UserSummary(autor, "Autor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
    }

    private Publicacion publicacionVisible() {
        return Publicacion.rehydrate(PublicacionId.newId(), UserId.of(UUID.randomUUID()), TipoPublicacion.MANUAL,
                null, "hola", List.of(new MediaPublicacion("wall", "muro/x/1.jpg", "image/jpeg", 0)), false,
                CLOCK.now(), CLOCK.now());
    }

    private Comentario comentarioDe(UserId autorId, PublicacionId publicacionId) {
        return Comentario.rehydrate(ComentarioId.newId(), publicacionId, autorId, "que lindo", false, CLOCK.now(),
                CLOCK.now());
    }

    @Test
    void editarUnComentarioAjenoFalla() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(autor, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));

        var command = new EditarComentarioCommand(otro, comentario.id(), "nuevo texto");
        assertThatThrownBy(() -> service.editar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveComentarioPort, never()).save(any());
    }

    @Test
    void ocultarUnComentarioAjenoSinModerarFalla() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(autor, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));

        var command = new OcultarComentarioCommand(otro, comentario.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    /** Regresion de seguridad: un actor que no existe en `usuarios` (X-Actor-Id
     * manipulado, sin JWT real todavia — CLAUDE.MD "Bloqueado") intentando moderar debe
     * recibir 403 (NotAuthorizedException), nunca 404 (NoSuchElementException). Un 404 en
     * este punto delataria, por el mensaje, que el comentario SI existe (el otro unico
     * 404 posible en esta ruta es "comentario no encontrado", ya descartado antes de
     * llegar aca) — fail-closed, ver docs/MODULO_COMMUNITY.md sec. 5. */
    @Test
    void ocultarConActorInexistenteEsRechazadoComo403NoComo404() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(autor, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));
        UserId fantasma = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(fantasma)).thenReturn(Optional.empty());

        var command = new OcultarComentarioCommand(fantasma, comentario.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void moderadorPuedeOcultarUnComentarioAjeno() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(autor, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));
        when(loadComentarioPort.contar(publicacion.id())).thenReturn(0);
        when(saveComentarioPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.ocultar(new OcultarComentarioCommand(admin, comentario.id()));

        assertThat(comentario.oculto()).isTrue();
        assertThat(resultado.cantidadComentarios()).isZero();
    }

    @Test
    void escribirEnUnaPublicacionOcultaFalla() {
        Publicacion oculta = Publicacion.rehydrate(PublicacionId.newId(), autor, TipoPublicacion.MANUAL, null,
                "hola", List.of(new MediaPublicacion("wall", "x", "image/jpeg", 0)), true, CLOCK.now(), CLOCK.now());
        when(loadPublicacionPort.porId(oculta.id())).thenReturn(Optional.of(oculta));

        var command = new EscribirComentarioCommand(autor, oculta.id(), "hola");
        assertThatThrownBy(() -> service.escribir(command)).isInstanceOf(java.util.NoSuchElementException.class);
    }

    /** Regresion (auditoria E2E adversarial): escribir/editar/ocultar un comentario no
     * chequeaban el estado del actor en absoluto -- un SUSPENDIDO pasaba sin problema. */
    @Test
    void escribirConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible();
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        var command = new EscribirComentarioCommand(suspendido, publicacion.id(), "hola");
        assertThatThrownBy(() -> service.escribir(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveComentarioPort, never()).save(any());
    }

    @Test
    void editarConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(suspendido, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));

        var command = new EditarComentarioCommand(suspendido, comentario.id(), "nuevo texto");
        assertThatThrownBy(() -> service.editar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveComentarioPort, never()).save(any());
    }

    @Test
    void ocultarConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible();
        Comentario comentario = comentarioDe(suspendido, publicacion.id());
        when(loadComentarioPort.porId(comentario.id())).thenReturn(Optional.of(comentario));

        var command = new OcultarComentarioCommand(suspendido, comentario.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveComentarioPort, never()).save(any());
    }
}
