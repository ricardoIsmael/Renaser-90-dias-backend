package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase.ResultadoDelCierre;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreDelSemaforoServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 9, 8);
    private static final LocalDate DIA_NOVENTA = LocalDate.of(2026, 12, 6);
    private static final LocalDate VIERNES_18 = LocalDate.of(2026, 9, 18);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);

    /** Sábado 26, 00:30 en Lima: la semana del 19 al 25 ya cerró. */
    private static final FixedClock SABADO_0030_LIMA = FixedClock.at(Instant.parse("2026-09-26T05:30:00Z"));
    /**
     * Sábado 26 a las 04:30 UTC, que en Lima todavía es viernes 25 a las 23:30 (regla 02 §3): un reloj
     * a una hora UTC "segura" escondería exactamente el bug de cerrar la semana antes de tiempo.
     */
    private static final FixedClock VIERNES_2330_LIMA = FixedClock.at(Instant.parse("2026-09-26T04:30:00Z"));

    @Mock
    private ProgramasActivadosFinder programasFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ConteoDiarioHabitosFinder habitosFinder;
    @Mock
    private ConteoDiarioObjetivosFinder objetivosFinder;

    private final SemaforoEnMemoria tablas = new SemaforoEnMemoria();
    private final List<Object> eventos = new ArrayList<>();
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final Map<UserId, UserSummary> usuarios = new HashMap<>();

    @BeforeEach
    void padron() {
        usuarios.put(ana, new UserSummary(ana, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        usuarios.put(beto, new UserSummary(beto, "Beto", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        lenient().when(userSummaryFinder.findByIds(anyCollection())).thenAnswer(inv -> {
            Map<UserId, UserSummary> pedidos = new HashMap<>();
            for (Object id : (Collection<?>) inv.getArgument(0)) {
                if (usuarios.containsKey(id)) {
                    pedidos.put((UserId) id, usuarios.get(id));
                }
            }
            return pedidos;
        });
        lenient().when(objetivosFinder.porParticipanteEntre(anyCollection(), any(), any())).thenReturn(Map.of());
    }

    private CierreDelSemaforoService servicio(FixedClock reloj) {
        CierreDeParticipanteService deUno = new CierreDeParticipanteService(tablas, tablas, eventos::add);
        return new CierreDelSemaforoService(programasFinder, userSummaryFinder, tablas, tablas, habitosFinder,
                objetivosFinder, deUno, reloj);
    }

    private static ProgramaActivado programa(UserId id) {
        return new ProgramaActivado(id, LIMA, DIA_UNO, DIA_NOVENTA);
    }

    private void semanaYaCerrada(UserId id, LocalDate viernes) {
        tablas.semanas.computeIfAbsent(id, k -> new TreeMap<>()).put(viernes, new FotoSemanal(viernes,
                viernes.minusDays(6), new BigDecimal("50.0"), 7, 7, ReglaDelSemaforo.VERSION_FORMULA,
                Instant.parse("2026-09-19T05:25:00Z")));
    }

    /** Cuatro de cuatro hábitos cada día del sábado 19 al viernes 25. */
    private static List<ConteoDelDia> todoCumplidoDel19Al25() {
        List<ConteoDelDia> dias = new ArrayList<>();
        for (LocalDate f = LocalDate.of(2026, 9, 19); !f.isAfter(VIERNES_25); f = f.plusDays(1)) {
            dias.add(new ConteoDelDia(f, 4, 4));
        }
        return dias;
    }

    @Test
    void elSabadoALasCeroTreintaDeLimaCierraLaSemanaYAvisa() {
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(List.of(programa(ana)));
        semanaYaCerrada(ana, VIERNES_18);
        when(habitosFinder.porParticipanteEntre(List.of(ana), LocalDate.of(2026, 9, 19), VIERNES_25))
                .thenReturn(Map.of(ana, todoCumplidoDel19Al25()));

        ResultadoDelCierre resultado = servicio(SABADO_0030_LIMA).cerrarPendientes();

        assertThat(resultado.diasGuardados()).isEqualTo(7);
        assertThat(resultado.semanasCerradas()).isEqualTo(1);
        FotoSemanal foto = tablas.semanas.get(ana).get(VIERNES_25);
        assertThat(foto.porcentaje()).isEqualByComparingTo("100.0");
        assertThat(eventos).singleElement().isInstanceOfSatisfying(SemanaDelSemaforoCerradaEvent.class, e -> {
            assertThat(e.participanteId()).isEqualTo(ana.value());
            assertThat(e.hasta()).isEqualTo(VIERNES_25);
            assertThat(e.color()).isEqualTo(ColorSemaforo.VERDE);
            assertThat(e.claveDeduplicacion())
                    .isEqualTo(SemanaDelSemaforoCerradaEvent.claveDe(ana.value(), VIERNES_25));
        });
    }

    @Test
    void aLasVeintitresTreintaDelViernesEnLimaLaSemanaTodaviaNoSeCierra() {
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(List.of(programa(ana)));
        semanaYaCerrada(ana, VIERNES_18);
        when(habitosFinder.porParticipanteEntre(List.of(ana), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 24)))
                .thenReturn(Map.of(ana, todoCumplidoDel19Al25().subList(0, 6)));

        ResultadoDelCierre resultado = servicio(VIERNES_2330_LIMA).cerrarPendientes();

        assertThat(resultado.semanasCerradas()).isZero();
        assertThat(tablas.semanas.get(ana)).doesNotContainKey(VIERNES_25);
        assertThat(tablas.dias.get(ana).lastKey()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(eventos).isEmpty();
    }

    @Test
    void correrloDosVecesDaLoMismoYNoAvisaDosVeces() {
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(List.of(programa(ana)));
        semanaYaCerrada(ana, VIERNES_18);
        lenient().when(habitosFinder.porParticipanteEntre(eq(List.of(ana)), any(), any()))
                .thenReturn(Map.of(ana, todoCumplidoDel19Al25()));

        servicio(SABADO_0030_LIMA).cerrarPendientes();
        ResultadoDelCierre segunda = servicio(SABADO_0030_LIMA).cerrarPendientes();

        assertThat(segunda.semanasCerradas()).isZero();
        assertThat(segunda.diasGuardados()).isZero();
        assertThat(eventos).hasSize(1);
    }

    @Test
    void unaSemanaCerradaFueraDelFinDeSemanaQuedaEnElHistorialSinAvisar() {
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(List.of(programa(ana)));
        semanaYaCerrada(ana, VIERNES_18);
        when(habitosFinder.porParticipanteEntre(eq(List.of(ana)), any(), any()))
                .thenReturn(Map.of(ana, todoCumplidoDel19Al25()));

        servicio(FixedClock.at(Instant.parse("2026-09-28T15:00:00Z"))).cerrarPendientes();

        assertThat(tablas.semanas.get(ana)).containsKey(VIERNES_25);
        assertThat(eventos).isEmpty();
    }

    @Test
    void unaCuentaSuspendidaNoSeMide() {
        usuarios.put(ana, new UserSummary(ana, "Ana", null, UserRole.TRAINEE, UserStatus.SUSPENDED));
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(List.of(programa(ana)));

        ResultadoDelCierre resultado = servicio(SABADO_0030_LIMA).cerrarPendientes();

        assertThat(resultado.evaluados()).isZero();
        assertThat(tablas.dias).doesNotContainKey(ana);
    }

    @Test
    void quienFallaNoFrenaAlRestoYLasConsultasSonEnLote() {
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE))
                .thenReturn(List.of(programa(ana), programa(beto)));
        semanaYaCerrada(ana, VIERNES_18);
        semanaYaCerrada(beto, VIERNES_18);
        tablas.fallanAlGuardar.add(ana);
        when(habitosFinder.porParticipanteEntre(eq(List.of(ana, beto)), any(), any()))
                .thenReturn(Map.of(beto, todoCumplidoDel19Al25()));

        ResultadoDelCierre resultado = servicio(SABADO_0030_LIMA).cerrarPendientes();

        assertThat(resultado.fallidos()).isEqualTo(1);
        assertThat(tablas.semanas.get(beto)).containsKey(VIERNES_25);
        verify(habitosFinder, times(1)).porParticipanteEntre(anyCollection(), any(), any());
        verify(objetivosFinder, times(1)).porParticipanteEntre(anyCollection(), any(), any());
    }

    @Test
    void unaPaginaLlenaPideLaSiguiente() {
        List<ProgramaActivado> llena = new ArrayList<>();
        for (int i = 0; i < CierreDelSemaforoService.TAMANO_LOTE; i++) {
            llena.add(new ProgramaActivado(UserId.of(UUID.randomUUID()), LIMA, null, null));
        }
        when(programasFinder.pagina(0, CierreDelSemaforoService.TAMANO_LOTE)).thenReturn(llena);
        when(programasFinder.pagina(CierreDelSemaforoService.TAMANO_LOTE, CierreDelSemaforoService.TAMANO_LOTE))
                .thenReturn(List.of());

        servicio(SABADO_0030_LIMA).cerrarPendientes();

        verify(programasFinder).pagina(CierreDelSemaforoService.TAMANO_LOTE, CierreDelSemaforoService.TAMANO_LOTE);
    }
}
