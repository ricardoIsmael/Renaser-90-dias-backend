package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase.RevisarManualmenteCommand;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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
class EvidenciaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private LoadEvidenciaPort loadEvidenciaPort;
    @Mock
    private SaveEvidenciaPort saveEvidenciaPort;
    @Mock
    private ValidacionIAPort validacionIAPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private EvidenciaService service;

    @BeforeEach
    void setUp() {
        service = new EvidenciaService(loadEvidenciaPort, saveEvidenciaPort, validacionIAPort, userSummaryFinder,
                CLOCK);
        lenient().when(saveEvidenciaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserSummary activo(UserId id, UserRole role) {
        return new UserSummary(id, "Fixture", null, role, UserStatus.ACTIVE);
    }

    private static UserSummary suspendido(UserId id) {
        return new UserSummary(id, "Fixture", null, UserRole.TRAINEE, UserStatus.SUSPENDED);
    }

    private Evidencia evidenciaDe(UserId participanteId) {
        return Evidencia.registrar(participanteId, new DestinoEvidencia.RegistroHabito(UUID.randomUUID()),
                TipoEvidencia.TEXTO, null, null, "hecho", null, null, null, false, CLOCK.now(), CLOCK);
    }

    private RegistrarEvidenciaComando comandoTexto(UserId participanteId) {
        return new RegistrarEvidenciaComando(participanteId, new DestinoEvidencia.RegistroHabito(UUID.randomUUID()),
                TipoEvidencia.TEXTO, null, null, "hecho", null, null, null, false, CLOCK.now());
    }

    // ---- registrar (defensa en profundidad, CLAUDE.MD §0.3) ----

    @Test
    @DisplayName("actor SUSPENDIDO no puede registrar evidencia propia, aunque el llamador ya haya validado")
    void actorSuspendidoNoRegistraEvidencia() {
        UserId participanteId = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(participanteId)).thenReturn(Optional.of(suspendido(participanteId)));

        assertThatThrownBy(() -> service.registrar(comandoTexto(participanteId)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveEvidenciaPort, never()).save(any());
    }

    @Test
    void actorActivoRegistraEvidenciaPendiente() {
        UserId participanteId = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(participanteId)).thenReturn(Optional.of(activo(participanteId, UserRole.TRAINEE)));

        var resultado = service.registrar(comandoTexto(participanteId));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        verify(saveEvidenciaPort).save(any());
    }

    // ---- consulta: dueño vs. ajena (salvo admin) ----

    @Test
    void elDuenoPuedeVerSuPropiaEvidencia() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));

        Evidencia resultado = service.porId(duenoId, evidencia.id());

        assertThat(resultado).isEqualTo(evidencia);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un actor no puede ver evidencia ajena salvo admin")
    void actorNoPuedeVerEvidenciaAjenaSinSerAdmin() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(otro)).thenReturn(Optional.of(activo(otro, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.porId(otro, evidencia.id())).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unAdminPuedeVerEvidenciaAjena() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        Evidencia resultado = service.porId(admin, evidencia.id());

        assertThat(resultado).isEqualTo(evidencia);
    }

    @Test
    void evidenciaInexistenteLanzaNoSuchElement() {
        EvidenciaId id = EvidenciaId.newId();
        when(loadEvidenciaPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.porId(UserId.of(UUID.randomUUID()), id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ---- revision manual / anulacion: solo admin ----

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (TRAINEE) no revisa manualmente -> NotAuthorizedException")
    void traineeNoPuedeRevisarManualmente() {
        UserId trainee = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(trainee)).thenReturn(Optional.of(activo(trainee, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.revisar(new RevisarManualmenteCommand(trainee, EvidenciaId.newId(), true, "x")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void adminRevisaManualmenteYAprueba() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        evidencia.registrarIntentoFallido();
        evidencia.registrarIntentoFallido();
        evidencia.registrarIntentoFallido();
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        Evidencia resultado = service.revisar(new RevisarManualmenteCommand(admin, evidencia.id(), true, "ok"));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.VALIDA);
    }

    @Test
    void adminAnulaVeredicto() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        evidencia.aprobarPorIa();
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        Evidencia resultado = service.anular(new AnularVeredictoCommand(admin, evidencia.id(), "duplicada"));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
    }

    @Test
    @DisplayName("actor ADMIN pero cuenta SUSPENDIDA -> NotAuthorizedException")
    void adminSuspendidoRechazado() {
        UserId adminSuspendido = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(suspendido(adminSuspendido)));

        assertThatThrownBy(() -> service.anular(new AnularVeredictoCommand(adminSuspendido, EvidenciaId.newId(), "x")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ---- cola de validacion: SIN IA, NoOp siempre NO_DISPONIBLE ----

    @Test
    @DisplayName("SIN IA: cada corrida incrementa intentosIa; al tercero cae a REVISION_MANUAL")
    void procesarLoteSinIaIncrementaIntentosHastaRevisionManual() {
        Evidencia pendiente = evidenciaDe(UserId.of(UUID.randomUUID()));
        when(loadEvidenciaPort.pendientesLote(any(), org.mockito.ArgumentMatchers.eq(25)))
                .thenReturn(List.of(pendiente));
        when(validacionIAPort.validar(pendiente)).thenReturn(ResultadoValidacionIA.NO_DISPONIBLE);

        int procesadas = service.procesarLote();

        assertThat(procesadas).isEqualTo(1);
        assertThat(pendiente.intentosIa()).isEqualTo(1);
        assertThat(pendiente.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        verify(saveEvidenciaPort).save(pendiente);
    }

    @Test
    void procesarLoteSinPendientesNoLlamaAlPuertoDeIa() {
        when(loadEvidenciaPort.pendientesLote(any(), org.mockito.ArgumentMatchers.eq(25))).thenReturn(List.of());

        int procesadas = service.procesarLote();

        assertThat(procesadas).isZero();
        verify(validacionIAPort, never()).validar(any());
    }
}
