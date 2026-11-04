package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.WhatsappDeLaFichaFinder;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase.GetTraineeDetailCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ListTraineesCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;
import com.renaser.os.users.application.ports.out.ajustediaprograma.LoadUltimoAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.ConsultarResumenParticipacionPort;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.in.emergencia.ResolverEmergenciaAlCambiarDiaUseCase;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.PlazoDeGracia;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El WhatsApp de la Ficha Inicial en el panel de personas de Administración (D-320, issue #78): el listado lo pide <b>una sola vez
 * para toda la página</b> (nada de una consulta por fila), la ficha de una persona lo trae, quien no lo tiene sale con {@code null} y
 * <b>solo después del gate de admin</b>: quien no administra no llega a pedirlo.
 */
@ExtendWith(MockitoExtension.class)
class ListarPersonasConWhatsappTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-04T10:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private ConsultarResumenParticipacionPort consultarResumen;
    @Mock
    private LoadUltimoAjusteDiaProgramaPort ultimoAjuste;
    @Mock
    private WhatsappDeLaFichaFinder whatsappDeLaFicha;

    private ParticipacionProgramaService service;

    @BeforeEach
    void armar() {
        service = new ParticipacionProgramaService(new RequireActiveUserGuard(loadUserPort),
                mock(LoadParticipacionProgramaPort.class), mock(SaveParticipacionProgramaPort.class),
                mock(DeleteParticipacionProgramaPort.class), consultarResumen, mock(LoadMentorProfilePort.class), loadUserPort,
                new RequireAdminGuard(loadUserPort), mock(SaveAjusteDiaProgramaPort.class), ultimoAjuste,
                UUID::randomUUID, CLOCK, new PlazoDeGracia(30), mock(ResolverEmergenciaAlCambiarDiaUseCase.class),
                ProgramaPropioDelPersonal.escuela(), whatsappDeLaFicha);
    }

    private User usuario(UserId id, UserRole rol) {
        return User.rehydrate(id, new Email(id + "@renaser.com"), rol, UserStatus.ACTIVE, "Fixture " + id, null, null, null, null);
    }

    private static ResumenTraineeAdmin fila(UserId id, UserRole rol) {
        return new ResumenTraineeAdmin(id, "Persona " + id, id + "@renaser.com", UserStatus.ACTIVE, 5,
                FasePrograma.PHASE_1_REBIRTH, null, null, rol);
    }

    private UserId administrador() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(admin)).thenReturn(Optional.of(usuario(admin, UserRole.ADMIN)));
        return admin;
    }

    @Test
    @DisplayName("el listado pide el WhatsApp UNA vez con los ids de toda la página y lo pone en cada fila; null para quien no lo tiene")
    void unaSolaConsultaParaTodaLaPagina() {
        UserId admin = administrador();
        UserId ana = UserId.of(UUID.randomUUID());
        UserId luis = UserId.of(UUID.randomUUID());
        UserId sinFicha = UserId.of(UUID.randomUUID());
        when(consultarResumen.listarAprendices(0, 20, null, false, null, AlcanceDeFormacion.todas(), false))
                .thenReturn(List.of(fila(ana, UserRole.TRAINEE), fila(luis, UserRole.MENTOR), fila(sinFicha, UserRole.TRAINEE)));
        when(consultarResumen.contarAprendices(null, false, null, AlcanceDeFormacion.todas(), false)).thenReturn(3L);
        when(whatsappDeLaFicha.de(any())).thenReturn(Map.of(ana, "+51 987654321", luis, "+34 600111222"));

        var pagina = service.listar(new ListTraineesCommand(admin, 0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserId>> pedidos = ArgumentCaptor.forClass(Collection.class);
        verify(whatsappDeLaFicha, times(1)).de(pedidos.capture());
        assertThat(pedidos.getValue()).containsExactlyInAnyOrder(ana, luis, sinFicha);
        assertThat(pagina.contenido()).extracting(ResumenTraineeAdmin::whatsapp)
                .containsExactly("+51 987654321", "+34 600111222", null);
        assertThat(pagina.total()).isEqualTo(3L);
    }

    @Test
    @DisplayName("una página vacía no deja de funcionar: pide el WhatsApp de nadie y devuelve la lista vacía")
    void paginaVacia() {
        UserId admin = administrador();
        when(consultarResumen.listarAprendices(0, 20, null, false, null, AlcanceDeFormacion.todas(), false)).thenReturn(List.of());
        when(consultarResumen.contarAprendices(null, false, null, AlcanceDeFormacion.todas(), false)).thenReturn(0L);
        when(whatsappDeLaFicha.de(List.of())).thenReturn(Map.of());

        assertThat(service.listar(new ListTraineesCommand(admin, 0, 20)).contenido()).isEmpty();
    }

    @Test
    @DisplayName("quien no administra recibe 403 y el WhatsApp nunca se lee (ni siquiera se pide el padrón)")
    void elListadoNoLeeNadaSinElGate() {
        for (UserRole rol : List.of(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.TRAINEE, UserRole.TRAINEE_GUIDE)) {
            UserId actor = UserId.of(UUID.randomUUID());
            when(loadUserPort.byId(actor)).thenReturn(Optional.of(usuario(actor, rol)));

            assertThatThrownBy(() -> service.listar(new ListTraineesCommand(actor, 0, 20)))
                    .as("rol " + rol).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(whatsappDeLaFicha, consultarResumen);
    }

    @Test
    @DisplayName("la ficha de una persona trae su WhatsApp; sin ficha, null")
    void detalleConYSinWhatsapp() {
        UserId admin = administrador();
        UserId ana = UserId.of(UUID.randomUUID());
        UserId sinFicha = UserId.of(UUID.randomUUID());
        for (UserId id : List.of(ana, sinFicha)) {
            when(loadUserPort.byId(id)).thenReturn(Optional.of(usuario(id, UserRole.TRAINEE)));
            when(consultarResumen.resumenDe(id)).thenReturn(Optional.of(participacion(id)));
            when(ultimoAjuste.ultimoDe(id)).thenReturn(Optional.empty());
        }
        when(whatsappDeLaFicha.de(List.of(ana))).thenReturn(Map.of(ana, "+51 987654321"));
        when(whatsappDeLaFicha.de(List.of(sinFicha))).thenReturn(Map.of());

        assertThat(service.obtener(new GetTraineeDetailCommand(admin, ana)).whatsapp()).isEqualTo("+51 987654321");
        assertThat(service.obtener(new GetTraineeDetailCommand(admin, sinFicha)).whatsapp()).isNull();
    }

    @Test
    @DisplayName("el detalle: quien no administra recibe 403 y el WhatsApp nunca se lee")
    void elDetalleNoLeeNadaSinElGate() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        when(loadUserPort.byId(aprendiz)).thenReturn(Optional.of(usuario(aprendiz, UserRole.TRAINEE)));
        for (UserRole rol : List.of(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.TRAINEE, UserRole.TRAINEE_GUIDE)) {
            UserId actor = UserId.of(UUID.randomUUID());
            when(loadUserPort.byId(actor)).thenReturn(Optional.of(usuario(actor, rol)));

            assertThatThrownBy(() -> service.obtener(new GetTraineeDetailCommand(actor, aprendiz)))
                    .as("rol " + rol).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(whatsappDeLaFicha, consultarResumen);
    }

    private static ParticipacionPrograma participacion(UserId id) {
        return new ParticipacionPrograma(id, true, 10, LocalDate.of(2026, 10, 25), ZoneId.of("America/Lima"),
                FasePrograma.PHASE_2_DEVELOPMENT, null, null, UserRole.TRAINEE, false, true);
    }
}
