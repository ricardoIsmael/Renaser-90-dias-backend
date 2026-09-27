package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.MotivoDePausa;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
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
import org.junit.jupiter.api.DisplayName;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

/**
 * D-209: los días en que la cuenta estuvo suspendida no se miden. La suspensión entra por el caso de uso
 * (lo que hace el listener del evento de {@code users}) y la mide el barrido real, con las tablas en
 * memoria.
 *
 * <p>Los instantes de la suspensión y de la reactivación caen <b>entre las 00:00 y las 05:00 UTC</b>, que
 * en Lima todavía es el día anterior (regla 02 §3, E-91): con la fecha UTC, el primer y el último día
 * suspendidos se correrían uno y el porcentaje cambiaría.
 *
 * <p>La semana es la del sábado 19 al viernes 25 de septiembre de 2026. {@code habits} le sigue
 * generando 4 hábitos por día aunque la cuenta esté suspendida (quedan vencidos): los conteos de esos
 * días son los que la persona no pudo cumplir.
 */
@ExtendWith(MockitoExtension.class)
class SuspensionDelSemaforoServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 9, 8);
    private static final LocalDate DIA_NOVENTA = LocalDate.of(2026, 12, 6);
    private static final LocalDate SABADO_19 = LocalDate.of(2026, 9, 19);
    private static final LocalDate VIERNES_18 = LocalDate.of(2026, 9, 18);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);

    /** Sábado 26, 00:30 en Lima: el barrido que cierra la semana del 19 al 25. */
    private static final FixedClock SABADO_0030_LIMA = FixedClock.at(Instant.parse("2026-09-26T05:30:00Z"));

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
    private final Map<UserId, UserSummary> usuarios = new HashMap<>();
    private final Map<UserId, ProgramaActivado> programas = new HashMap<>();
    private final Map<LocalDate, ConteoDelDia> habitosDeAna = new TreeMap<>();
    private final UserId ana = UserId.of(UUID.randomUUID());

    @BeforeEach
    void padron() {
        programas.put(ana, new ProgramaActivado(ana, LIMA, DIA_UNO, DIA_NOVENTA));
        cuenta(ana, UserStatus.ACTIVE);
        lenient().when(programasFinder.pagina(anyInt(), anyInt())).thenAnswer(inv ->
                (int) inv.getArgument(0) == 0 ? List.copyOf(programas.values()) : List.of());
        lenient().when(programasFinder.de(any())).thenAnswer(inv -> Optional.ofNullable(programas.get(inv.getArgument(0))));
        lenient().when(programasFinder.deVarios(anyCollection())).thenAnswer(inv -> soloDe(programas, inv.getArgument(0)));
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.ofNullable(usuarios.get(inv.getArgument(0))));
        lenient().when(userSummaryFinder.findByIds(anyCollection())).thenAnswer(inv -> soloDe(usuarios, inv.getArgument(0)));
        lenient().when(habitosFinder.porParticipanteEntre(anyCollection(), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(1);
            LocalDate hasta = inv.getArgument(2);
            List<ConteoDelDia> delRango = habitosDeAna.values().stream()
                    .filter(c -> !c.fecha().isBefore(desde) && !c.fecha().isAfter(hasta)).toList();
            return Map.of(ana, delRango);
        });
        lenient().when(objetivosFinder.porParticipanteEntre(anyCollection(), any(), any())).thenReturn(Map.of());
        // La semana anterior ya estaba cerrada: la corrida del sábado solo cierra la del 19 al 25.
        tablas.semanas.computeIfAbsent(ana, k -> new TreeMap<>()).put(VIERNES_18, new FotoSemanal(VIERNES_18,
                VIERNES_18.minusDays(6), new BigDecimal("90.0"), 7, 7, ReglaDelSemaforo.VERSION_FORMULA,
                Instant.parse("2026-09-19T05:25:00Z")));
    }

    private static <V> Map<UserId, V> soloDe(Map<UserId, V> todos, Collection<?> ids) {
        Map<UserId, V> pedidos = new HashMap<>();
        ids.forEach(id -> Optional.ofNullable(todos.get(id)).ifPresent(v -> pedidos.put((UserId) id, v)));
        return pedidos;
    }

    private void cuenta(UserId id, UserStatus estado) {
        usuarios.put(id, new UserSummary(id, "Ana", null, UserRole.TRAINEE, estado));
    }

    /** Cuatro hábitos por día del sábado 19 al viernes 25, con lo que cumplió cada día. */
    private void semanaDeAna(int... cumplidos) {
        for (int i = 0; i < cumplidos.length; i++) {
            LocalDate fecha = SABADO_19.plusDays(i);
            habitosDeAna.put(fecha, new ConteoDelDia(fecha, 4, cumplidos[i]));
        }
    }

    private SuspensionDelSemaforoService suspensiones(FixedClock reloj) {
        return new SuspensionDelSemaforoService(programasFinder, userSummaryFinder, tablas, reloj);
    }

    private CierreDelSemaforoService barrido(FixedClock reloj) {
        CierreDeParticipanteService deUno = new CierreDeParticipanteService(tablas, tablas, eventos::add);
        return new CierreDelSemaforoService(programasFinder, userSummaryFinder, tablas, tablas, habitosFinder,
                objetivosFinder, deUno, reloj);
    }

    private ConsultaDelSemaforoService consulta(FixedClock reloj) {
        return new ConsultaDelSemaforoService(new LecturaDeMediciones(programasFinder, tablas, tablas, reloj),
                userSummaryFinder, tablas, tablas);
    }

    private void suspender(UserId id, String instante) {
        cuenta(id, UserStatus.SUSPENDED);
        suspensiones(SABADO_0030_LIMA).alSuspender(id, Instant.parse(instante));
    }

    private void reactivar(UserId id, String instante) {
        cuenta(id, UserStatus.ACTIVE);
        suspensiones(SABADO_0030_LIMA).alReactivar(id, Instant.parse(instante));
    }

    private FotoSemanal fotoDelViernes25() {
        return tablas.semanas.get(ana).get(VIERNES_25);
    }

    private static List<EstadoDiaSemaforo> estados(VentanaDelSemaforo ventana) {
        return ventana.dias().stream().map(DiaDelSemaforo::estado).toList();
    }

    // ---------------------------------------------------------------------------------------
    // Las tres semanas del pedido
    // ---------------------------------------------------------------------------------------

    /**
     * Suspendida el lunes 21 a las 21:00 de Lima (martes 02:00 UTC) y reactivada el jueves 24 a las 23:30
     * (viernes 04:30 UTC). Antes: (100 + 100 + 25 + 0 + 0 + 0 + 100) / 7 = 46,4, rojo, y el aviso del
     * sábado decía rojo. Ahora se miden sábado, domingo y viernes: 100, verde.
     */
    @Test
    @DisplayName("una semana con días suspendidos en el medio ya no queda en rojo")
    void unaSemanaConDiasSuspendidosEnElMedioYaNoQuedaEnRojo() {
        semanaDeAna(4, 4, 1, 0, 0, 0, 4);
        suspender(ana, "2026-09-22T02:00:00Z");
        reactivar(ana, "2026-09-25T04:30:00Z");

        barrido(SABADO_0030_LIMA).cerrarPendientes();

        FotoSemanal foto = fotoDelViernes25();
        assertThat(foto.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(foto.porcentaje()).isEqualByComparingTo("100.0");
        assertThat(foto.diasConDatos()).isEqualTo(3);
        assertThat(foto.diasMedidos()).isEqualTo(3);
        assertThat(tablas.dias.get(ana)).containsOnlyKeys(SABADO_19, SABADO_19.plusDays(1), VIERNES_25);
        assertThat(eventos).singleElement().isInstanceOfSatisfying(SemanaDelSemaforoCerradaEvent.class,
                e -> assertThat(e.color()).isEqualTo(ColorSemaforo.VERDE));
        var suspendido = EstadoDiaSemaforo.CUENTA_SUSPENDIDA;
        assertThat(estados(consulta(SABADO_0030_LIMA).vigenteDe(List.of(ana)).get(ana)))
                .containsExactly(EstadoDiaSemaforo.MEDIDO, EstadoDiaSemaforo.MEDIDO, suspendido, suspendido,
                        suspendido, suspendido, EstadoDiaSemaforo.MEDIDO);
    }

    /**
     * Suspendida el sábado 19 a la 01:00 de Lima (después del cierre de la semana anterior) y reactivada el
     * viernes 25 a las 23:00 de Lima (sábado 04:00 UTC). Antes: siete días en 0 %, rojo y con aviso. Ahora
     * no hay ningún día medido: «Sin datos», ni rojo ni verde, y el sábado no se le avisa nada.
     */
    @Test
    @DisplayName("una semana suspendida entera queda «Sin datos» y sin aviso")
    void unaSemanaSuspendidaEnteraQuedaSinDatos() {
        semanaDeAna(0, 0, 0, 0, 0, 0, 0);
        suspender(ana, "2026-09-19T06:00:00Z");
        reactivar(ana, "2026-09-26T04:00:00Z");

        barrido(SABADO_0030_LIMA).cerrarPendientes();

        FotoSemanal foto = fotoDelViernes25();
        assertThat(foto.porcentaje()).isNull();
        assertThat(foto.color()).isEqualTo(ColorSemaforo.SIN_DATOS);
        assertThat(foto.diasMedidos()).isZero();
        assertThat(eventos).isEmpty();
        VentanaDelSemaforo vigente = consulta(SABADO_0030_LIMA).vigenteDe(List.of(ana)).get(ana);
        assertThat(vigente.color()).isEqualTo(ColorSemaforo.SIN_DATOS);
        assertThat(estados(vigente)).containsOnly(EstadoDiaSemaforo.CUENTA_SUSPENDIDA);
    }

    /**
     * La semana como pasa de verdad: el barrido corre cada día. Suspendida el lunes 21 a las 10:00 de
     * Lima; el martes el barrido no la toca; reactivada el miércoles 23 a las 22:30 de Lima (jueves 03:30
     * UTC); el jueves el barrido se pone al día sin medir lunes, martes ni miércoles, y el sábado cierra con
     * sábado, domingo, jueves y viernes: (100 + 75 + 100 + 100) / 4 = 93,8. Antes: 64,3, amarillo.
     */
    @Test
    @DisplayName("una reactivación a mitad de semana: se mide desde el día siguiente")
    void unaReactivacionAMitadDeSemana() {
        semanaDeAna(4, 3, 2, 0, 1, 4, 4);
        barrido(FixedClock.at(Instant.parse("2026-09-21T05:30:00Z"))).cerrarPendientes();   // lunes 00:30
        suspender(ana, "2026-09-21T15:00:00Z");                                               // lunes 10:00
        barrido(FixedClock.at(Instant.parse("2026-09-22T05:30:00Z"))).cerrarPendientes();   // martes 00:30
        assertThat(tablas.dias.get(ana)).containsOnlyKeys(SABADO_19, SABADO_19.plusDays(1));

        reactivar(ana, "2026-09-24T03:30:00Z");                                               // miércoles 22:30
        barrido(FixedClock.at(Instant.parse("2026-09-24T05:30:00Z"))).cerrarPendientes();   // jueves 00:30
        assertThat(tablas.dias.get(ana)).containsOnlyKeys(SABADO_19, SABADO_19.plusDays(1));

        barrido(SABADO_0030_LIMA).cerrarPendientes();

        FotoSemanal foto = fotoDelViernes25();
        assertThat(foto.porcentaje()).isEqualByComparingTo("93.8");
        assertThat(foto.color()).isEqualTo(ColorSemaforo.VERDE);
        assertThat(foto.diasConDatos()).isEqualTo(4);
        assertThat(tablas.dias.get(ana)).containsOnlyKeys(SABADO_19, SABADO_19.plusDays(1),
                LocalDate.of(2026, 9, 24), VIERNES_25);
        PausaDeMedicion suspension = tablas.pausas.get(ana).getFirst();
        assertThat(suspension.desde()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(suspension.reanudadaEl()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    // ---------------------------------------------------------------------------------------
    // Reentregas, orden y bordes del evento
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("el día sale de la zona de la persona: las 02:00 UTC del martes son el lunes en Lima")
    void elDiaDeLaSuspensionEsElDeSuZona() {
        suspender(ana, "2026-09-22T02:00:00Z");

        PausaDeMedicion suspension = tablas.pausas.get(ana).getFirst();
        assertThat(suspension.motivo()).isEqualTo(MotivoDePausa.CUENTA_SUSPENDIDA);
        assertThat(suspension.desde()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(suspension.creadaEn()).isEqualTo(Instant.parse("2026-09-22T02:00:00Z"));
        assertThat(suspension.suspensionEnCurso()).isTrue();
    }

    @Test
    @DisplayName("la misma suspensión entregada dos veces se anota una sola vez")
    void unaReentregaNoDuplica() {
        suspender(ana, "2026-09-22T02:00:00Z");
        suspender(ana, "2026-09-22T02:00:00Z");
        reactivar(ana, "2026-09-25T04:30:00Z");
        reactivar(ana, "2026-09-25T04:30:00Z");
        suspensiones(SABADO_0030_LIMA).alSuspender(ana, Instant.parse("2026-09-22T02:00:00Z"));

        assertThat(tablas.pausas.get(ana)).singleElement().satisfies(p -> {
            assertThat(p.suspensionEnCurso()).isFalse();
            assertThat(p.reanudadaEl()).isEqualTo(VIERNES_25);
        });
    }

    /**
     * Un reintento del outbox trae la suspensión cuando la reactivación ya se procesó. La cuenta ya no está
     * suspendida: se la cierra hoy (jueves 24, 04:00 UTC = miércoles 23 en Lima), nunca queda abierta para
     * siempre.
     */
    @Test
    @DisplayName("una suspensión que llega después de su reactivación se cierra hoy, en su zona")
    void unaSuspensionQueLlegaTardeSeCierraHoy() {
        suspensiones(SABADO_0030_LIMA).alReactivar(ana, Instant.parse("2026-09-22T20:00:00Z"));
        assertThat(tablas.pausas).doesNotContainKey(ana);

        suspensiones(FixedClock.at(Instant.parse("2026-09-24T04:00:00Z")))
                .alSuspender(ana, Instant.parse("2026-09-22T02:00:00Z"));

        PausaDeMedicion suspension = tablas.pausas.get(ana).getFirst();
        assertThat(suspension.desde()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(suspension.ultimoDiaPausado()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(suspension.suspensionEnCurso()).isFalse();
    }

    @Test
    @DisplayName("una reactivación no cierra una suspensión que empezó después de ella")
    void unaReactivacionViejaNoCierraUnaSuspensionNueva() {
        suspender(ana, "2026-09-23T15:00:00Z");

        suspensiones(SABADO_0030_LIMA).alReactivar(ana, Instant.parse("2026-09-22T20:00:00Z"));

        assertThat(tablas.pausas.get(ana)).singleElement().satisfies(p -> assertThat(p.suspensionEnCurso()).isTrue());
    }

    /** Una suspensión anterior al despliegue sin relleno (no hay nada anotado): la reactivación no inventa fechas. */
    @Test
    @DisplayName("una reactivación sin suspensión anotada no inventa nada")
    void unaReactivacionSinSuspensionAnotadaNoHaceNada() {
        reactivar(ana, "2026-09-25T04:30:00Z");

        assertThat(tablas.pausas).doesNotContainKey(ana);
    }

    @Test
    @DisplayName("sin programa activado no hay semáforo: no se anota nada")
    void sinProgramaActivadoNoSeAnotaNada() {
        UserId beto = UserId.of(UUID.randomUUID());
        cuenta(beto, UserStatus.SUSPENDED);

        suspensiones(SABADO_0030_LIMA).alSuspender(beto, Instant.parse("2026-09-22T02:00:00Z"));

        assertThat(tablas.pausas).doesNotContainKey(beto);
    }

    /**
     * La suspensión no es una pausa de la persona. Mientras sigue suspendida, lo que ve administración de
     * ella no trae {@code pausa} ni {@code pausado} (y no falla por no tener fecha de regreso). Ya
     * reactivada, «volver a medir» del staff no la toca.
     */
    @Test
    @DisplayName("la suspensión no se muestra como pausa ni la toca la pausa del staff")
    void laSuspensionNoSeMuestraComoPausa() {
        UserId luisa = UserId.of(UUID.randomUUID());
        programas.put(luisa, new ProgramaActivado(luisa, LIMA, DIA_UNO, DIA_NOVENTA));
        usuarios.put(luisa, new UserSummary(luisa, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED));
        FixedClock miercolesALasOnceDeLaNoche = FixedClock.at(Instant.parse("2026-09-24T04:00:00Z"));
        suspensiones(miercolesALasOnceDeLaNoche).alSuspender(luisa, Instant.parse("2026-09-22T02:00:00Z"));
        ConsultaDelSemaforoService consulta = consulta(miercolesALasOnceDeLaNoche);

        assertThat(consulta.detalleDe(luisa, 8).pausa()).isNull();
        assertThat(consulta.resumenParaHoy(luisa)).get().satisfies(r -> assertThat(r.pausado()).isFalse());
        // Su ventana va del miércoles 16 al martes 22: el lunes 21 y el martes 22, con la cuenta suspendida.
        assertThat(estados(consulta.detalleDe(luisa, 8).vigente())).endsWith(EstadoDiaSemaforo.PENDIENTE,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA);

        usuarios.put(luisa, new UserSummary(luisa, "Luisa", null, UserRole.MENTOR, UserStatus.ACTIVE));
        suspensiones(miercolesALasOnceDeLaNoche).alReactivar(luisa, Instant.parse("2026-09-24T03:00:00Z"));
        new PausaDelSemaforoService(userSummaryFinder, programasFinder, tablas, consulta, miercolesALasOnceDeLaNoche)
                .reanudar(luisa);

        assertThat(consulta.consultar(luisa, 8).pausa()).isNull();
        assertThat(tablas.pausas.get(luisa)).singleElement()
                .satisfies(p -> assertThat(p.reanudadaEl()).isEqualTo(LocalDate.of(2026, 9, 24)));
    }
}
