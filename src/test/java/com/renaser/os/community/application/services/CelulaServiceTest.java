package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase.CrearCelulaCommand;
import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
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
class CelulaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private SaveCelulaPort saveCelulaPort;
    @Mock
    private EliminarCelulaPort eliminarCelulaPort;
    @Mock
    private LoadCohortePort loadCohortePort;
    @Mock
    private ExistePerfilMentorPort existePerfilMentorPort;
    @Mock
    private ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort;
    @Mock
    private ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    @Mock
    private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private CelulaService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId trainee = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new CelulaService(loadCelulaPort, saveCelulaPort, eliminarCelulaPort, loadCohortePort,
                existePerfilMentorPort, consultarMiembrosCelulaPort, consultarCelulaDeParticipantePort,
                consultarPerfilUsuarioPort, userSummaryFinder, events, CLOCK);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(trainee))
                .thenReturn(Optional.of(new UserSummary(trainee, "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    private Celula celulaExistente() {
        return Celula.rehydrate(CelulaId.newId(), "Celula 1", null, CohorteId.newId(), null, null, CLOCK.now(),
                CLOCK.now());
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearCelulaCommand(mentor, "Celula 1", CohorteId.newId(), null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void asignarMentorSinPerfilPropioFalla() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(false);
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalStateException.class);
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    void asignarUnTraineeComoLiderFalla() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), trainee);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarMentorYaLiderDeOtraCelulaFalla() {
        Celula celula = celulaExistente();
        Celula otraCelula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(loadCelulaPort.porMentor(mentor)).thenReturn(Optional.of(otraCelula));
        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        assertThatThrownBy(() -> service.asignar(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void asignarMentorElegibleFunciona() {
        Celula celula = celulaExistente();
        when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(loadCelulaPort.porMentor(mentor)).thenReturn(Optional.empty());
        when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new AsignarMentorCelulaCommand(admin, celula.id(), mentor);
        Celula actualizada = service.asignar(command);
        assertThat(actualizada.mentorId()).isEqualTo(mentor);
    }

    @Test
    void miCelulaSinCelulaAsignadaEsVacio() {
        when(consultarCelulaDeParticipantePort.celulaDeUsuario(trainee)).thenReturn(Optional.empty());
        assertThat(service.miCelula(trainee)).isEmpty();
    }
}
