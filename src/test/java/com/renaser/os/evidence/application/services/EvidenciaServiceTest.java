package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase.ListarEvidenciaAdminComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.PaginaEvidencias;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase.RevisarManualmenteCommand;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort.FiltroEvidencia;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;

    private EvidenciaService service;

    @BeforeEach
    void setUp() {
        service = new EvidenciaService(loadEvidenciaPort, saveEvidenciaPort, validacionIAPort, userSummaryFinder,
                participacionFinder, ajustarPuntosPort, CLOCK);
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
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (TRAINEE) no anula -> NotAuthorizedException")
    void traineeNoPuedeAnular() {
        UserId trainee = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(trainee)).thenReturn(Optional.of(activo(trainee, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.anular(new AnularVeredictoCommand(trainee, EvidenciaId.newId(), "x")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (MENTOR) no anula -> NotAuthorizedException")
    void mentorNoPuedeAnular() {
        UserId mentor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(mentor)).thenReturn(Optional.of(activo(mentor, UserRole.MENTOR)));

        assertThatThrownBy(() -> service.anular(new AnularVeredictoCommand(mentor, EvidenciaId.newId(), "x")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ---- anular = "override" del backend viejo: idempotente + revierte puntos ----

    @Test
    @DisplayName("anular es idempotente: la segunda llamada no lanza, no vuelve a ajustar puntos ni pisa las notas")
    void anularEsIdempotente() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        evidencia.aprobarPorIa();
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        service.anular(new AnularVeredictoCommand(admin, evidencia.id(), "primera anulacion"));
        Evidencia resultado = service.anular(new AnularVeredictoCommand(admin, evidencia.id(), "segunda, no-op"));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
        assertThat(resultado.notasValidacion()).isEqualTo("primera anulacion");
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("override real: anular una evidencia con penalizacion aplicada la revierte via points.api")
    void anularRevierteLaPenalizacionCuandoEstabaAplicada() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = Evidencia.rehydrate(EvidenciaId.newId(), duenoId,
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), TipoEvidencia.TEXTO, null, null, "hecho",
                null, CLOCK.now(), null, null, false, EstadoValidacion.RECHAZADA, "rechazada por IA", 1, true, false,
                CLOCK.now());
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        Evidencia resultado = service.anular(new AnularVeredictoCommand(admin, evidencia.id(), "admin revierte"));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
        assertThat(resultado.penalizacionAplicada()).isFalse();
        verify(ajustarPuntosPort).ajustar(duenoId, MotivoPuntos.INVALID_EVIDENCE_REVOKED,
                Evidencia.PENALIZACION_EVIDENCIA_INVALIDA_PUNTOS,
                "Veredicto de evidencia invalida anulado por admin: se revierte la penalizacion");
    }

    @Test
    @DisplayName("anular sin penalizacion aplicada no toca points.api")
    void anularSinPenalizacionNoAjustaPuntos() {
        UserId duenoId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        Evidencia evidencia = evidenciaDe(duenoId);
        evidencia.aprobarPorIa();
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));

        service.anular(new AnularVeredictoCommand(admin, evidencia.id(), "sin penalizacion"));

        verify(ajustarPuntosPort, never()).ajustar(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
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

    // ---- listado general (hueco #19): dueño / mentor asignado / admin ----

    private ParticipacionPrograma participacionDe(UserId participanteId, UserId mentorId) {
        return new ParticipacionPrograma(participanteId, true, 20, LocalDate.of(2026, 1, 1),
                ZoneId.of("America/Lima"), FasePrograma.initial(), null, mentorId, UserRole.TRAINEE, false);
    }

    @Test
    void elDuenoListaSuPropiaEvidenciaSinIndicarParticipanteId() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(dueno)).thenReturn(Optional.of(activo(dueno, UserRole.TRAINEE)));
        when(loadEvidenciaPort.buscar(any(), any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of());

        service.listar(new ListarEvidenciaComando(dueno, null, null, null, null, null, null));

        verify(loadEvidenciaPort).buscar(new FiltroEvidencia(dueno, null, null, null, null), null, 20);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un TRAINEE no puede pedir evidencia de otro participante")
    void actorNoAdminNoPuedeListarEvidenciaAjena() {
        UserId actor = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(actor)).thenReturn(Optional.of(activo(actor, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(actor, otro, null, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void adminListaEvidenciaDeCualquierParticipante() {
        UserId admin = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));
        when(loadEvidenciaPort.buscar(any(), any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of());

        service.listar(new ListarEvidenciaComando(admin, otro, EstadoValidacion.REVISION_MANUAL, null, null, null,
                null));

        verify(loadEvidenciaPort).buscar(
                new FiltroEvidencia(otro, EstadoValidacion.REVISION_MANUAL, null, null, null), null, 20);
    }

    @Test
    @DisplayName("un MENTOR sin participanteId no puede listar: no hay 'todos mis aprendices' en este alcance")
    void mentorSinParticipanteIdEsRechazado() {
        UserId mentor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(mentor)).thenReturn(Optional.of(activo(mentor, UserRole.MENTOR)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(mentor, null, null, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("mismo bug que E-38 (docs/BITACORA_ERRORES.md): rol MENTOR correcto pero NO asignado a ese "
            + "aprendiz -> 403")
    void mentorNoAsignadoNoPuedeListarEvidenciaDeOtroAprendiz() {
        UserId mentor = UserId.of(UUID.randomUUID());
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UserId otroMentor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(mentor)).thenReturn(Optional.of(activo(mentor, UserRole.MENTOR)));
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionDe(aprendiz, otroMentor)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(mentor, aprendiz, null, null, null, null,
                null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void mentorAsignadoPuedeListarLaEvidenciaDeSuAprendiz() {
        UserId mentor = UserId.of(UUID.randomUUID());
        UserId aprendiz = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(mentor)).thenReturn(Optional.of(activo(mentor, UserRole.MENTOR)));
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionDe(aprendiz, mentor)));
        when(loadEvidenciaPort.buscar(any(), any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of());

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaComando(mentor, aprendiz, null, null, null, null,
                null));

        assertThat(pagina.evidencias()).isEmpty();
        verify(loadEvidenciaPort).buscar(new FiltroEvidencia(aprendiz, null, null, null, null), null, 20);
    }

    @Test
    void actorSuspendidoNoPuedeListar() {
        UserId suspendidoId = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(suspendidoId)).thenReturn(Optional.of(suspendido(suspendidoId)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(suspendidoId, null, null, null, null,
                null, null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("keyset: cuando el puerto devuelve limite+1 filas, la pagina se trunca y expone el siguiente cursor")
    void listarTruncaYExponeCursorCuandoHayMas() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));
        List<Evidencia> veintiuna = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            veintiuna.add(evidenciaDe(UserId.of(UUID.randomUUID())));
        }
        when(loadEvidenciaPort.buscar(any(), any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(veintiuna);

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaComando(admin, null, null, null, null, null,
                null));

        assertThat(pagina.evidencias()).hasSize(20);
        assertThat(pagina.siguienteCursor()).isEqualTo(veintiuna.get(19).creadoEn());
    }

    @Test
    void comandoDeListadoRechazaRangoDeFechasInvertido() {
        UserId actor = UserId.of(UUID.randomUUID());
        Instant desde = Instant.parse("2026-08-25T10:00:00Z");
        Instant hasta = Instant.parse("2026-08-24T10:00:00Z");

        assertThatThrownBy(() -> new ListarEvidenciaComando(actor, null, null, null, desde, hasta, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- listado admin (hueco #20): solo ADMIN/ALCHEMIST, sin scoping de dueño/mentor ----

    @Test
    @DisplayName("CLAUDE.MD §0.3: TRAINEE no accede al listado del panel admin")
    void traineeNoAccedeAlListadoAdmin() {
        UserId trainee = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(trainee)).thenReturn(Optional.of(activo(trainee, UserRole.TRAINEE)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaAdminComando(trainee, null,
                EstadoValidacion.REVISION_MANUAL, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("MENTOR tampoco accede al listado del panel admin (no confirmado para evidencia, ver docs)")
    void mentorNoAccedeAlListadoAdmin() {
        UserId mentor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(mentor)).thenReturn(Optional.of(activo(mentor, UserRole.MENTOR)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaAdminComando(mentor, null,
                EstadoValidacion.REVISION_MANUAL, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void adminAccedeAlListadoDeRevisionManual() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(admin)).thenReturn(Optional.of(activo(admin, UserRole.ADMIN)));
        when(loadEvidenciaPort.buscar(any(), any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of());

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaAdminComando(admin, null,
                EstadoValidacion.REVISION_MANUAL, null, null, null, null));

        assertThat(pagina.evidencias()).isEmpty();
        verify(loadEvidenciaPort).buscar(
                new FiltroEvidencia(null, EstadoValidacion.REVISION_MANUAL, null, null, null), null, 20);
    }

    @Test
    @DisplayName("actor ADMIN pero cuenta SUSPENDIDA -> NotAuthorizedException, tambien en el listado admin")
    void adminSuspendidoNoAccedeAlListadoAdmin() {
        UserId adminSuspendido = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(suspendido(adminSuspendido)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaAdminComando(adminSuspendido, null, null, null,
                null, null, null))).isInstanceOf(NotAuthorizedException.class);
    }
}
