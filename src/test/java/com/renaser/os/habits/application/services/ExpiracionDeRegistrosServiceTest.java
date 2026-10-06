package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase.ResultadoDelBarrido;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.participante.ConsultarZonasDeParticipantesPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarPendientesVencidosPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarPendientesVencidosPort.PendientesDeParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-534 — el barrido que expira por el dia local de cada participante, sin base de datos. El de punta a punta, con
 * Postgres y hora por hora, es {@code ExpiracionPorZonaIT}.
 *
 * <p>El reloj esta a las 03:00 UTC del 10/11 (regla 02 §3): en Lima son las 22:00 del 9, en Los Angeles (UTC−8) las
 * 19:00 del 9 y en Lagos (UTC+1) las 04:00 del 10.
 */
@ExtendWith(MockitoExtension.class)
class ExpiracionDeRegistrosServiceTest {

    private static final Instant TRES_UTC = Instant.parse("2026-11-10T03:00:00Z");
    private static final LocalDate OCHO = LocalDate.of(2026, 11, 8);
    private static final LocalDate NUEVE = LocalDate.of(2026, 11, 9);
    private static final LocalDate DIEZ = LocalDate.of(2026, 11, 10);

    @Mock
    private ConsultarPendientesVencidosPort pendientesPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private ConsultarZonasDeParticipantesPort zonasPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    /** Sin stubbing: el callback de la transaccion propia corre igual con un mock vacio (C-6). */
    @Mock
    private PlatformTransactionManager transactionManager;

    private ExpiracionDeRegistrosService service;

    private final UserId lima = UserId.of(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    private final UserId angeles = UserId.of(UUID.fromString("00000000-0000-4000-8000-000000000002"));
    private final UserId lagos = UserId.of(UUID.fromString("00000000-0000-4000-8000-000000000003"));

    @BeforeEach
    void setUp() {
        service = new ExpiracionDeRegistrosService(pendientesPort, loadRegistroPort, saveRegistroPort, zonasPort,
                progresoPort, FixedClock.at(TRES_UTC), transactionManager);
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("de madrugada UTC cada uno vence hasta SU hoy: Lima el 8, Los Angeles el 8, Lagos el 9")
    void cadaUnoVenceHastaSuHoy() {
        unaPagina(new PendientesDeParticipante(lima, OCHO), new PendientesDeParticipante(angeles, OCHO),
                new PendientesDeParticipante(lagos, NUEVE));
        when(zonasPort.deProgramasActivados(List.of(lima, angeles, lagos))).thenReturn(Map.of(
                lima, ZoneId.of("America/Lima"), angeles, ZoneId.of("America/Los_Angeles"),
                lagos, ZoneId.of("Africa/Lagos")));
        RegistroHabito delOchoLima = pendiente(lima, OCHO);
        RegistroHabito delOchoAngeles = pendiente(angeles, OCHO);
        RegistroHabito delNueveLagos = pendiente(lagos, NUEVE);
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(lima, NUEVE)).thenReturn(List.of(delOchoLima));
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(angeles, NUEVE)).thenReturn(List.of(delOchoAngeles));
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(lagos, DIEZ)).thenReturn(List.of(delNueveLagos));

        ResultadoDelBarrido resultado = service.expirarDiasTerminados();

        assertThat(List.of(delOchoLima, delOchoAngeles, delNueveLagos))
                .allSatisfy(r -> assertThat(r.estado()).isEqualTo(EstadoRegistro.EXPIRADO));
        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(3, 3, 0));
    }

    @Test
    @DisplayName("si lo mas viejo de alguien es de su hoy, no se leen sus registros: el dominio ya decidio")
    void sinNadaTerminadoNoSeLeeNada() {
        unaPagina(new PendientesDeParticipante(lima, NUEVE), new PendientesDeParticipante(angeles, NUEVE));
        when(zonasPort.deProgramasActivados(any())).thenReturn(Map.of(lima, ZoneId.of("America/Lima"),
                angeles, ZoneId.of("America/Los_Angeles")));

        ResultadoDelBarrido resultado = service.expirarDiasTerminados();

        verify(loadRegistroPort, never()).pendientesDeParticipanteAnterioresA(any(), any());
        verify(saveRegistroPort, never()).save(any());
        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(2, 0, 0));
    }

    @Test
    @DisplayName("pagina por keyset con el tope de la zona mas adelantada, y corta cuando una pagina viene incompleta")
    void paginaHastaElFinal() {
        List<PendientesDeParticipante> llena = IntStream.range(0, ExpiracionDeRegistrosService.TAMANO_LOTE)
                .mapToObj(i -> new PendientesDeParticipante(UserId.of(UUID.randomUUID()), NUEVE)).toList();
        UserId ultimoDeLaPrimera = llena.getLast().participanteId();
        LocalDate tope = DIEZ; // 03:00 UTC + 18 h = 21:00 del 10
        when(pendientesPort.pagina(tope, null, ExpiracionDeRegistrosService.TAMANO_LOTE)).thenReturn(llena);
        when(pendientesPort.pagina(tope, ultimoDeLaPrimera, ExpiracionDeRegistrosService.TAMANO_LOTE))
                .thenReturn(List.of(new PendientesDeParticipante(lima, NUEVE)));
        when(zonasPort.deProgramasActivados(any())).thenReturn(Map.of());
        lenient().when(progresoPort.deParticipante(any())).thenReturn(Optional.of(progresoEn("America/Lima")));

        ResultadoDelBarrido resultado = service.expirarDiasTerminados();

        assertThat(resultado.participantes()).isEqualTo(ExpiracionDeRegistrosService.TAMANO_LOTE + 1);
        verify(pendientesPort, org.mockito.Mockito.times(2)).pagina(eq(tope), any(), anyInt());
    }

    @Test
    @DisplayName("quien no activo su programa no viene en el lote: su zona se lee sola")
    void sinProgramaActivadoSeLeeSuZonaSola() {
        unaPagina(new PendientesDeParticipante(angeles, OCHO));
        when(zonasPort.deProgramasActivados(any())).thenReturn(Map.of());
        when(progresoPort.deParticipante(angeles)).thenReturn(Optional.of(progresoEn("America/Los_Angeles")));
        RegistroHabito delOcho = pendiente(angeles, OCHO);
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(angeles, NUEVE)).thenReturn(List.of(delOcho));

        service.expirarDiasTerminados();

        assertThat(delOcho.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }

    @Test
    @DisplayName("un participante con la zona rota no frena a los demas, ni aunque rompa la lectura en lote")
    void unParticipanteQueFallaNoFrenaAlResto() {
        UserId roto = UserId.of(UUID.fromString("00000000-0000-4000-8000-000000000000"));
        unaPagina(new PendientesDeParticipante(roto, OCHO), new PendientesDeParticipante(lima, OCHO));
        when(zonasPort.deProgramasActivados(any())).thenThrow(new DateTimeException("Zona/Inexistente"));
        when(progresoPort.deParticipante(roto)).thenReturn(Optional.of(progresoEn("Zona/Inexistente")));
        when(progresoPort.deParticipante(lima)).thenReturn(Optional.of(progresoEn("America/Lima")));
        RegistroHabito deLima = pendiente(lima, OCHO);
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(lima, NUEVE)).thenReturn(List.of(deLima));

        ResultadoDelBarrido resultado = service.expirarDiasTerminados();

        assertThat(deLima.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(2, 1, 1));
        verify(loadRegistroPort, never()).pendientesDeParticipanteAnterioresA(eq(roto), any());
    }

    /**
     * C-6 (antes en {@code RegistroServiceTest}): una fila que falla al guardar no tumba el barrido de las demas.
     * Queda PENDIENTE en la base y la proxima corrida la vuelve a intentar.
     */
    @Test
    @DisplayName("una fila que falla al guardar no tumba el barrido de las demas (C-6)")
    void unaFilaQueFallaNoTumbaLasDemas() {
        unaPagina(new PendientesDeParticipante(lima, OCHO.minusDays(2)));
        when(zonasPort.deProgramasActivados(any())).thenReturn(Map.of(lima, ZoneId.of("America/Lima")));
        RegistroHabito r1 = pendiente(lima, OCHO.minusDays(2));
        RegistroHabito r2 = pendiente(lima, OCHO.minusDays(1));
        RegistroHabito r3 = pendiente(lima, OCHO);
        when(loadRegistroPort.pendientesDeParticipanteAnterioresA(lima, NUEVE)).thenReturn(List.of(r1, r2, r3));
        when(saveRegistroPort.save(r2)).thenThrow(new IllegalStateException("fila corrupta simulada"));

        ResultadoDelBarrido resultado = service.expirarDiasTerminados();

        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(1, 2, 1));
        assertThat(r1.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        assertThat(r3.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }

    @Test
    @DisplayName("sin nadie con pendientes no consulta zonas ni registros")
    void padronVacio() {
        when(pendientesPort.pagina(any(), isNull(), anyInt())).thenReturn(List.of());

        assertThat(service.expirarDiasTerminados()).isEqualTo(ResultadoDelBarrido.VACIO);
        verify(zonasPort, never()).deProgramasActivados(any());
    }

    private void unaPagina(PendientesDeParticipante... pendientes) {
        when(pendientesPort.pagina(any(), isNull(), anyInt())).thenReturn(new ArrayList<>(List.of(pendientes)));
    }

    private static ProgresoParticipanteHabits progresoEn(String zona) {
        return new ProgresoParticipanteHabits(11, zona, RolParticipante.TRAINEE, false, true);
    }

    private static RegistroHabito pendiente(UserId quien, LocalDate fecha) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), quien, HabitoId.of(UUID.randomUUID()),
                fecha, 10, TipoDia.DISCIPLINA, false, Instant.parse("2026-11-01T12:00:00Z"));
    }
}
