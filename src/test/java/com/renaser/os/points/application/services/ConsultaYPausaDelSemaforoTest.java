package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.PausarSemaforoUseCase.PausarSemaforoCommand;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

/** La lectura del semáforo y la pausa del staff, con las tablas en memoria. */
@ExtendWith(MockitoExtension.class)
class ConsultaYPausaDelSemaforoTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Jueves 24 a las 10:00 de Lima: la ventana vigente va del 17 al 23. */
    private static final FixedClock JUEVES = FixedClock.at(Instant.parse("2026-09-24T15:00:00Z"));
    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    @Mock
    private ProgramasActivadosFinder programasFinder;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private final SemaforoEnMemoria tablas = new SemaforoEnMemoria();
    private final Map<UserId, UserSummary> usuarios = new HashMap<>();
    private final Map<UserId, ProgramaActivado> programas = new HashMap<>();
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId sinPrograma = UserId.of(UUID.randomUUID());

    private ConsultaDelSemaforoService consulta;
    private PausaDelSemaforoService pausa;

    @BeforeEach
    void preparar() {
        usuarios.put(aprendiz, new UserSummary(aprendiz, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        usuarios.put(mentor, new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.ACTIVE));
        usuarios.put(sinPrograma, new UserSummary(sinPrograma, "Sin", null, UserRole.MENTOR, UserStatus.ACTIVE));
        programas.put(aprendiz, new ProgramaActivado(aprendiz, LIMA, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 29)));
        programas.put(mentor, new ProgramaActivado(mentor, LIMA, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 29)));
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.getArgument(0))));
        lenient().when(programasFinder.de(any())).thenAnswer(inv -> Optional.ofNullable(programas.get(inv.getArgument(0))));
        lenient().when(programasFinder.deVarios(anyCollection())).thenAnswer(inv -> {
            Map<UserId, ProgramaActivado> pedidos = new HashMap<>();
            for (Object id : (Collection<?>) inv.getArgument(0)) {
                if (programas.containsKey(id)) {
                    pedidos.put((UserId) id, programas.get(id));
                }
            }
            return pedidos;
        });
        LecturaDeMediciones lectura = new LecturaDeMediciones(programasFinder, tablas, tablas, JUEVES);
        consulta = new ConsultaDelSemaforoService(lectura, userSummaryFinder, tablas, tablas);
        pausa = new PausaDelSemaforoService(userSummaryFinder, programasFinder, tablas, consulta, JUEVES);
    }

    private void diaGuardado(UserId id, LocalDate fecha, int programados, int cumplidos) {
        tablas.dias.computeIfAbsent(id, k -> new TreeMap<>())
                .put(fecha, new CumplimientoDelDia(fecha, programados, cumplidos, 0, 0));
    }

    @Test
    void laLecturaNoRecalculaSoloLeeLoGuardado() {
        diaGuardado(aprendiz, LocalDate.of(2026, 9, 22), 5, 3);   // 60
        diaGuardado(aprendiz, LocalDate.of(2026, 9, 23), 5, 2);   // 40

        Map<UserId, VentanaDelSemaforo> vigentes = consulta.vigenteDe(List.of(aprendiz, sinPrograma));

        assertThat(vigentes).containsOnlyKeys(aprendiz);
        VentanaDelSemaforo vigente = vigentes.get(aprendiz);
        assertThat(vigente.desde()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(vigente.porcentaje()).isEqualByComparingTo("50.0");
        assertThat(vigente.color()).isEqualTo(ColorSemaforo.ROJO);
        assertThat(vigente.dias().getFirst().estado()).isEqualTo(EstadoDiaSemaforo.PENDIENTE);
    }

    @Test
    void sinProgramaActivadoElDetalleNoAplica() {
        DetalleDelSemaforo detalle = consulta.consultar(sinPrograma, 8);

        assertThat(detalle.aplica()).isFalse();
        assertThat(detalle.vigente()).isNull();
        assertThat(detalle.semanas()).isEmpty();
    }

    @Test
    void paraElAprendizEsObligatorio() {
        assertThat(consulta.consultar(aprendiz, 8).obligatorio()).isTrue();
        assertThat(consulta.consultar(mentor, 8).obligatorio()).isFalse();
    }

    @Test
    void unaCuentaSuspendidaNoConsultaAunqueSuTokenSeaValido() {
        usuarios.put(mentor, new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED));

        assertThatThrownBy(() -> consulta.consultar(mentor, 8)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void unaSemanaQueNoTerminaEnViernesSeRechaza() {
        assertThatThrownBy(() -> consulta.semanaDe(List.of(aprendiz), HOY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elAprendizNoPuedePausarlo() {
        assertThatThrownBy(() -> pausa.pausar(new PausarSemaforoCommand(aprendiz, HOY.plusDays(3))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sinProgramaPropioNoHayQuePausar() {
        assertThatThrownBy(() -> pausa.pausar(new PausarSemaforoCommand(sinPrograma, HOY.plusDays(3))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void elStaffPausaHastaUnaFechaYSePuedeCambiarSinDuplicar() {
        DetalleDelSemaforo pausado = pausa.pausar(new PausarSemaforoCommand(mentor, HOY.plusDays(3)));
        pausa.pausar(new PausarSemaforoCommand(mentor, HOY.plusDays(10)));

        assertThat(pausado.pausa().desde()).isEqualTo(HOY);
        List<PausaDeMedicion> suyas = tablas.pausas.get(mentor);
        assertThat(suyas).singleElement().satisfies(p -> assertThat(p.hasta()).isEqualTo(HOY.plusDays(10)));
        assertThat(consulta.resumenParaHoy(mentor)).get().satisfies(r -> assertThat(r.pausado()).isTrue());
    }

    @Test
    void volverAEncenderloMideDesdeHoy() {
        pausa.pausar(new PausarSemaforoCommand(mentor, HOY.plusDays(3)));

        DetalleDelSemaforo detalle = pausa.reanudar(mentor);

        assertThat(detalle.pausa()).isNull();
        assertThat(tablas.pausas.get(mentor).getFirst().reanudadaEl()).isEqualTo(HOY);
    }

    @Test
    void unaCuentaSuspendidaNoPausa() {
        usuarios.put(mentor, new UserSummary(mentor, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED));

        assertThatThrownBy(() -> pausa.pausar(new PausarSemaforoCommand(mentor, HOY)))
                .isInstanceOf(NotAuthorizedException.class);
    }
}
