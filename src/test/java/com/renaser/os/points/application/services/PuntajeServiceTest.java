package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosManualmenteUseCase.AjustarPuntosManualmenteCommand;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosUseCase.AjustarPuntosCommand;
import com.renaser.os.points.application.ports.in.puntaje.RegistrarCoherenciaDiariaUseCase.RegistrarCoherenciaDiariaCommand;
import com.renaser.os.points.application.ports.out.ajuste.SaveAjustePort;
import com.renaser.os.points.application.ports.out.puntaje.LoadPuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.SaveHistorialCoherenciaPort;
import com.renaser.os.points.application.ports.out.puntaje.SavePuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.VerificarActorAdministrativoPort;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuntajeServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadPuntajePort loadPuntajePort;
    @Mock
    private SavePuntajePort savePuntajePort;
    @Mock
    private SaveAjustePort saveAjustePort;
    @Mock
    private SaveHistorialCoherenciaPort saveHistorialCoherenciaPort;
    @Mock
    private VerificarActorAdministrativoPort verificarActorAdministrativoPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;

    private PuntajeService service;

    @BeforeEach
    void setUp() {
        service = new PuntajeService(loadPuntajePort, savePuntajePort, saveAjustePort,
                saveHistorialCoherenciaPort, verificarActorAdministrativoPort, userSummaryFinder,
                participacionProgramaFinder, CLOCK);
        lenient().when(savePuntajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveAjustePort.save(any())).thenAnswer(inv -> {
            AjustePuntos a = inv.getArgument(0);
            return AjustePuntos.rehydrate(99L, a.participanteId(), a.motivo(), a.delta(), a.deltaAplicado(),
                    a.saldoPosterior(), a.nota(), a.creadoEn());
        });
        // Defaults del camino feliz: participante inscrito y actor activo. Los tests de
        // BUG-4/BUG-5 sobreescriben esto para probar el camino de rechazo.
        lenient().when(participacionProgramaFinder.deParticipante(any()))
                .thenAnswer(inv -> Optional.of(inscrito(inv.getArgument(0))));
        lenient().when(userSummaryFinder.findById(any()))
                .thenAnswer(inv -> Optional.of(activo(inv.getArgument(0))));
    }

    private static ParticipacionPrograma inscrito(UserId id) {
        return new ParticipacionPrograma(id, true, 17, null, null, null, null, null, UserRole.TRAINEE, false);
    }

    private static UserSummary activo(UserId id) {
        return new UserSummary(id, "Test", null, UserRole.TRAINEE, UserStatus.ACTIVE);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("P-06: ajustar() guarda el saldo Y el asiento del ledger, con el mismo deltaAplicado")
    void ajustarGuardaSaldoYAsientoConsistentes() {
        UserId id = participante();
        // C-12: cargarOInicializar() relee byParticipanteIdParaEscritura despues de asegurar la
        // fila (crearFilaInicialSiFalta) — primera llamada vacia (no hay fila todavia), segunda
        // ya con la fila recien creada (o la de quien haya ganado la carrera de creacion).
        when(loadPuntajePort.byParticipanteIdParaEscritura(id))
                .thenReturn(Optional.empty(), Optional.of(PuntajeParticipante.inicial(id, CLOCK)));

        AjustePuntos ajuste = service.ajustar(new AjustarPuntosCommand(id, MotivoPuntos.HABIT_COMPLETED, 10, "ok"));

        ArgumentCaptor<PuntajeParticipante> puntajeCaptor = ArgumentCaptor.forClass(PuntajeParticipante.class);
        verify(savePuntajePort).crearFilaInicialSiFalta(any());
        verify(savePuntajePort).save(puntajeCaptor.capture());
        verify(saveAjustePort).save(any());

        assertThat(ajuste.deltaAplicado()).isEqualTo(10);
        assertThat(ajuste.saldoPosterior()).isEqualTo(110); // 100 inicial + 10
        assertThat(puntajeCaptor.getValue().puntosLiga()).isEqualTo(110);
    }

    @Test
    @DisplayName("un participante sin fila previa arranca en 100 antes de aplicar el delta (lazy-init)")
    void lazyInitAntesDelPrimerAjuste() {
        UserId id = participante();
        when(loadPuntajePort.byParticipanteIdParaEscritura(id))
                .thenReturn(Optional.empty(), Optional.of(PuntajeParticipante.inicial(id, CLOCK)));

        AjustePuntos ajuste = service.ajustar(new AjustarPuntosCommand(id, MotivoPuntos.MISSED_HABIT, -1000, null));

        assertThat(ajuste.saldoPosterior()).isZero(); // piso 0, no negativo
        assertThat(ajuste.deltaAplicado()).isEqualTo(-100); // no -1000
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (TRAINEE/MENTOR) -> NotAuthorizedException (-> 403)")
    void ajusteManualRequiereActorAdministrativo() {
        UserId participanteId = participante();
        UserId actorId = participante();
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actorId)).thenReturn(false);

        assertThatThrownBy(() -> service.ajustarManualmente(
                new AjustarPuntosManualmenteCommand(participanteId, 10, "correccion", actorId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePuntajePort, never()).save(any());
        verify(saveAjustePort, never()).save(any());
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDED -> NotAuthorizedException (-> 403) aunque tuviera rol ADMIN")
    void ajusteManualRechazaActorSuspendido() {
        // esAdministrativoActivo() ya combina rol Y estado (ver VerificarActorAdministrativoPort/
        // ActorAdministrativoPersistenceAdapter, cuyo IT prueba el caso SUSPENDIDO contra Postgres
        // real); a nivel de servicio, un ADMIN suspendido llega igual con el puerto en false.
        UserId participanteId = participante();
        UserId actorSuspendidoId = participante();
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actorSuspendidoId)).thenReturn(false);

        assertThatThrownBy(() -> service.ajustarManualmente(
                new AjustarPuntosManualmenteCommand(participanteId, 10, "correccion", actorSuspendidoId)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("ajustarManualmente con actor autorizado fuerza motivo MANUAL_ADJUSTMENT (no lo elige el cliente)")
    void ajusteManualFuerzaMotivoManual() {
        UserId participanteId = participante();
        UserId actorId = participante();
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actorId)).thenReturn(true);
        when(loadPuntajePort.byParticipanteIdParaEscritura(participanteId))
                .thenReturn(Optional.empty(), Optional.of(PuntajeParticipante.inicial(participanteId, CLOCK)));

        service.ajustarManualmente(new AjustarPuntosManualmenteCommand(participanteId, -5, "correccion", actorId));

        ArgumentCaptor<AjustePuntos> captor = ArgumentCaptor.forClass(AjustePuntos.class);
        verify(saveAjustePort).save(captor.capture());
        assertThat(captor.getValue().motivo()).isEqualTo(MotivoPuntos.MANUAL_ADJUSTMENT);
    }

    @Test
    @DisplayName("consultar() no escribe nada (sin efectos secundarios) y devuelve el estado inicial si no existe fila")
    void consultarEsPuramenteDeLectura() {
        UserId id = participante();
        when(loadPuntajePort.byParticipanteId(id)).thenReturn(Optional.empty());

        PuntajeParticipante puntaje = service.consultar(id, id);

        assertThat(puntaje.puntosLiga()).isEqualTo(100);
        verify(savePuntajePort, never()).save(any());
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: consultar() con actor ajeno sin permiso administrativo -> NotAuthorizedException (-> 403)")
    void consultarRechazaActorAjenoSinPermiso() {
        UserId participanteId = participante();
        UserId actorId = participante();
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actorId)).thenReturn(false);

        assertThatThrownBy(() -> service.consultar(actorId, participanteId))
                .isInstanceOf(NotAuthorizedException.class);

        verify(loadPuntajePort, never()).byParticipanteId(any());
    }

    @Test
    @DisplayName("registrar() escribe historial + cache y, si toca, dispara el bono de racha en la MISMA operacion")
    void registrarCoherenciaDisparaBonoDeRachaCuandoCorresponde() {
        UserId id = participante();
        PuntajeParticipante existente = PuntajeParticipante.rehydrate(id, BigDecimal.valueOf(80), 100, 2, 2,
                CLOCK.now());
        when(loadPuntajePort.byParticipanteIdParaEscritura(id)).thenReturn(Optional.of(existente));

        service.registrar(new RegistrarCoherenciaDiariaCommand(id, CLOCK.today(), BigDecimal.valueOf(95), true));

        verify(saveHistorialCoherenciaPort).upsert(eq(id), eq(CLOCK.today()), eq(BigDecimal.valueOf(95)));
        // racha pasa de 2 a 3 -> corresponde bono (+5) -> segundo asiento en el ledger
        verify(saveAjustePort, times(1)).save(any());
        verify(savePuntajePort, times(2)).save(any()); // 1: coherencia+racha, 2: el ajuste del bono
    }

    @Test
    @DisplayName("registrar() con dia imperfecto NO dispara bono aunque corte la racha")
    void registrarCoherenciaSinBonoEnDiaImperfecto() {
        UserId id = participante();
        PuntajeParticipante existente = PuntajeParticipante.rehydrate(id, BigDecimal.valueOf(80), 100, 5, 5,
                CLOCK.now());
        when(loadPuntajePort.byParticipanteIdParaEscritura(id)).thenReturn(Optional.of(existente));

        service.registrar(new RegistrarCoherenciaDiariaCommand(id, CLOCK.today(), BigDecimal.valueOf(40), false));

        verify(saveAjustePort, never()).save(any());
    }

    @Test
    @DisplayName("registrar() no es idempotente por dia: llamarlo dos veces para el mismo dia perfecto "
            + "vuelve a sumar racha y, si el nuevo valor cae en el multiplo de 3, vuelve a aplicar el bono")
    void registrarCoherenciaLlamadoDosVecesElMismoDiaVuelveAAplicarElBono() {
        UserId id = participante();
        // racha en 2: el siguiente dia perfecto (racha 3) corresponde bono.
        PuntajeParticipante existente = PuntajeParticipante.rehydrate(id, BigDecimal.valueOf(80), 100, 2, 2,
                CLOCK.now());
        when(loadPuntajePort.byParticipanteIdParaEscritura(id)).thenReturn(Optional.of(existente));

        RegistrarCoherenciaDiariaCommand command =
                new RegistrarCoherenciaDiariaCommand(id, CLOCK.today(), BigDecimal.valueOf(95), true);
        service.registrar(command);
        service.registrar(command); // mismo dia, mismo comando, invocado de nuevo

        // dos llamados perfectos seguidos: racha 3 (bono) y racha 4 (sin bono) -> un solo asiento de bono
        verify(saveHistorialCoherenciaPort, times(2)).upsert(eq(id), eq(CLOCK.today()), eq(BigDecimal.valueOf(95)));
        verify(saveAjustePort, times(1)).save(any());
    }

    @Test
    @DisplayName("BUG-4: consultar() rechaza al propio actor si esta SUSPENDIDO, aunque sea su propio puntaje")
    void consultarRechazaAlPropioActorSuspendido() {
        UserId id = participante();
        when(userSummaryFinder.findById(id))
                .thenReturn(Optional.of(new UserSummary(id, "X", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.consultar(id, id)).isInstanceOf(NotAuthorizedException.class);

        verify(loadPuntajePort, never()).byParticipanteId(any());
    }

    @Test
    @DisplayName("BUG-5: ajustar puntos a alguien sin fila de participacion da 404 claro, no un 500 por FK")
    void ajustarRechazaParticipanteSinFilaDeParticipacion() {
        UserId participanteId = participante();
        UserId actorId = participante();
        when(loadPuntajePort.byParticipanteIdParaEscritura(participanteId)).thenReturn(Optional.empty());
        when(verificarActorAdministrativoPort.esAdministrativoActivo(actorId)).thenReturn(true);
        when(participacionProgramaFinder.deParticipante(participanteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ajustarManualmente(
                new AjustarPuntosManualmenteCommand(participanteId, 10, "x", actorId)))
                .isInstanceOf(NoSuchElementException.class);

        verify(savePuntajePort, never()).save(any());
        // C-12: requireInscrito() sigue corriendo ANTES de asegurar la fila inicial — no se
        // crea una fila de puntaje para alguien sin participacion solo porque perdio la carrera.
        verify(savePuntajePort, never()).crearFilaInicialSiFalta(any());
    }
}
