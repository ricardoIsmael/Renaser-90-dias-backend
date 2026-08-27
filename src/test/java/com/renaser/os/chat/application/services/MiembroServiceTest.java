package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiembroServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T10:00:00Z");

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort;
    @Mock
    private EsParticipantePort esParticipantePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private MiembroService service;

    private final Conversacion global = Conversacion.crearGlobal(AHORA);
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UserId otroActivo = UserId.of(UUID.randomUUID());
    private final UserId otroSuspendido = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new MiembroService(loadConversacionPort, listarUsuariosDeConversacionPort, esParticipantePort,
                userSummaryFinder);
        lenient().when(loadConversacionPort.global()).thenReturn(Optional.of(global));
        lenient().when(userSummaryFinder.findById(actor)).thenReturn(
                Optional.of(new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
    }

    @Test
    void directorioRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.listar(suspendido, null, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void directorioExcluyeAlPropioActorYAUnSuspendido() {
        when(listarUsuariosDeConversacionPort.usuariosDe(global.id()))
                .thenReturn(List.of(actor, otroActivo, otroSuspendido));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                actor, new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                otroActivo, new UserSummary(otroActivo, "Beatriz", null, UserRole.MENTOR, UserStatus.ACTIVE),
                otroSuspendido, new UserSummary(otroSuspendido, "Carlos", null, UserRole.TRAINEE,
                        UserStatus.SUSPENDED)));

        var pagina = service.listar(actor, null, null, 30);

        assertThat(pagina.miembros()).extracting(m -> m.id()).containsExactly(otroActivo);
    }

    @Test
    void directorioDevuelveLosCincoRoles() {
        UserId mentorLead = UserId.of(UUID.randomUUID());
        UserId adminId = UserId.of(UUID.randomUUID());
        UserId alquimista = UserId.of(UUID.randomUUID());
        when(listarUsuariosDeConversacionPort.usuariosDe(global.id()))
                .thenReturn(List.of(otroActivo, mentorLead, adminId, alquimista));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                otroActivo, new UserSummary(otroActivo, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE),
                mentorLead, new UserSummary(mentorLead, "Lider", null, UserRole.MENTOR_LEAD, UserStatus.ACTIVE),
                adminId, new UserSummary(adminId, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE),
                alquimista, new UserSummary(alquimista, "Alquimista", null, UserRole.ALCHEMIST, UserStatus.ACTIVE)));

        var pagina = service.listar(actor, null, null, 30);

        assertThat(pagina.miembros()).extracting(m -> m.rol())
                .containsExactlyInAnyOrder(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);
    }

    @Test
    void directorioFiltraPorNombreSinDistinguirMayusculas() {
        when(listarUsuariosDeConversacionPort.usuariosDe(global.id())).thenReturn(List.of(otroActivo));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                otroActivo, new UserSummary(otroActivo, "Beatriz Gomez", null, UserRole.MENTOR, UserStatus.ACTIVE)));

        var conMatch = service.listar(actor, "beatriz", null, 30);
        var sinMatch = service.listar(actor, "no existe nadie asi", null, 30);

        assertThat(conMatch.miembros()).hasSize(1);
        assertThat(sinMatch.miembros()).isEmpty();
    }

    @Test
    void directorioPaginaConCursorKeyset() {
        UserId c = UserId.of(UUID.randomUUID());
        UserId d = UserId.of(UUID.randomUUID());
        when(listarUsuariosDeConversacionPort.usuariosDe(global.id())).thenReturn(List.of(otroActivo, c, d));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                otroActivo, new UserSummary(otroActivo, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                c, new UserSummary(c, "Beto", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                d, new UserSummary(d, "Carla", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        var primera = service.listar(actor, null, null, 1);
        assertThat(primera.miembros()).extracting(m -> m.nombreCompleto()).containsExactly("Ana");
        assertThat(primera.hayMas()).isTrue();

        var segunda = service.listar(actor, null, primera.siguienteCursor(), 1);
        assertThat(segunda.miembros()).extracting(m -> m.nombreCompleto()).containsExactly("Beto");
    }

    @Test
    void rosterGlobalRechazaAQuienNoEsParticipante() {
        when(esParticipantePort.esParticipante(global.id(), actor)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(actor, null, 30)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void rosterGlobalIncluyeAUnSuspendidoADiferenciaDelDirectorio() {
        when(esParticipantePort.esParticipante(global.id(), actor)).thenReturn(true);
        when(listarUsuariosDeConversacionPort.usuariosDe(global.id())).thenReturn(List.of(actor, otroSuspendido));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                actor, new UserSummary(actor, "Actor", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                otroSuspendido, new UserSummary(otroSuspendido, "Carlos", null, UserRole.TRAINEE,
                        UserStatus.SUSPENDED)));

        var pagina = service.listar(actor, null, 30);

        assertThat(pagina.miembros()).extracting(m -> m.id()).containsExactlyInAnyOrder(actor, otroSuspendido);
    }
}
