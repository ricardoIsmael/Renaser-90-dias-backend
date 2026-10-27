package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarEstadoDelMapaPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MapaPendienteException;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraFijaException;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaMaestraServiceTest {

    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-07T10:00:00Z");
    private static final Clock CLOCK = FixedClock.at(AHORA);
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000009");

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private GuardarRocaMaestraPort guardarRocaMaestraPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private ConsultarEstadoDelMapaPort estadoDelMapaPort;
    @Mock
    private IdGenerator idGenerator;

    private RocaMaestraService service;

    @BeforeEach
    void setUp() {
        service = servicio(false);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(guardarRocaMaestraPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RocaMaestraService servicio(boolean exigirMapaActivo) {
        return new RocaMaestraService(loadRocaMaestraPort, guardarRocaMaestraPort, progresoPort, estadoDelMapaPort,
                CLOCK, idGenerator, exigirMapaActivo);
    }

    private void mapaActivo(UserId id, boolean activo) {
        when(estadoDelMapaPort.activo(id)).thenReturn(activo);
    }

    private static UserId actor() {
        return UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(10, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido, false);
    }

    /** Igual que {@link #progreso} pero con el programa ANDANDO: el caso de E-169. */
    private static ProgresoParticipanteRocks progresoActivado(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(10, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido, true);
    }

    private UserId traineeActivo() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        return id;
    }

    private static DefinirRocaMaestraCommand comando(UserId actor) {
        return new DefinirRocaMaestraCommand(actor, EjeObjetivo.TRABAJO,
                "Facturar 30.000 USD en contratos high-ticket", new BigDecimal("30000"), new BigDecimal("19500"),
                "USD", null, false);
    }

    /** El mismo pedido, pero el que manda la activacion del Mapa ({@code "origen": "MAPA"}). */
    private static DefinirRocaMaestraCommand comandoDelMapa(UserId actor, String objetivo, BigDecimal meta,
                                                             BigDecimal base) {
        return new DefinirRocaMaestraCommand(actor, EjeObjetivo.TRABAJO, objetivo, meta, base, "USD", base, true);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException aunque el rol sea correcto")
    void actorSuspendidoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void participanteInexistenteEs404() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void traineeActivoRecibeSusRocasMaestras() {
        UserId id = traineeActivo();
        RocaMaestra maestra = RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), id, EjeObjetivo.CUERPO,
                "objetivo", null, AYER);
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of(maestra));

        assertThat(service.misRocasMaestras(id)).containsExactly(maestra);
    }

    // ---------------------------------------------------------------------------------------
    // definir(): crear el objetivo de 90 dias; despues queda fijo y solo se registra el avance (D-234)
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("CLAUDE.MD §0.3: un rol sin permiso tampoco puede definir el objetivo")
    void definirRechazaRolSinPermiso() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.ADMIN, false)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un aprendiz SUSPENDIDO no puede definir el objetivo")
    void definirRechazaSuspendido() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("si el eje no tenia objetivo, se crea con la identidad del generador")
    void definirCreaCuandoNoExiste() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id().value()).isEqualTo(ID_GENERADO);
        assertThat(guardada.participanteId()).isEqualTo(id);
        assertThat(guardada.eje()).isEqualTo(EjeObjetivo.TRABAJO);
        assertThat(guardada.creadoEn()).isEqualTo(AHORA);
        assertThat(guardada.meta().porcentaje()).isEqualTo(65);
    }

    private RocaMaestraId yaDefinida(UserId id, BigDecimal avance) {
        RocaMaestraId yaExistente = RocaMaestraId.of(UUID.randomUUID());
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(
                RocaMaestra.rehydrate(yaExistente, id, EjeObjetivo.TRABAJO,
                        "Facturar 30.000 USD en contratos high-ticket",
                        new MetaCuantitativa(new BigDecimal("30000.00"), avance, "USD", null), AYER, AYER)));
        return yaExistente;
    }

    @Test
    @DisplayName("D-234, Mapa activo: la misma definicion otra vez (reintento del Mapa) se acepta y deja la roca igual")
    void definirLoMismoEsIdempotente() {
        UserId id = traineeActivo();
        mapaActivo(id, true);
        RocaMaestraId yaExistente = yaDefinida(id, new BigDecimal("19500.00"));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id()).as("misma roca, no una segunda").isEqualTo(yaExistente);
        assertThat(guardada.actualizadoEn()).as("nada cambio").isEqualTo(AYER);
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("D-234, Mapa activo: si solo cambia el avance, se registra el progreso sobre la misma roca")
    void definirConOtroAvanceRegistraElProgreso() {
        UserId id = traineeActivo();
        mapaActivo(id, true);
        RocaMaestraId yaExistente = yaDefinida(id, new BigDecimal("10000.00"));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id()).isEqualTo(yaExistente);
        assertThat(guardada.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(guardada.actualizadoEn()).isEqualTo(AHORA);
        assertThat(guardada.meta().avance()).isEqualByComparingTo("19500");
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("D-234, Mapa activo: cambiar objetivo, meta, unidad o linea base de una roca ya definida se rechaza sin guardar")
    void definirRechazaCambiarLoFijo() {
        UserId id = traineeActivo();
        mapaActivo(id, true);
        yaDefinida(id, new BigDecimal("19500.00"));
        String objetivo = "Facturar 30.000 USD en contratos high-ticket";
        BigDecimal avance = new BigDecimal("19500");
        List<DefinirRocaMaestraCommand> cambios = List.of(
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Otro objetivo", new BigDecimal("30000"),
                        avance, "USD", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, objetivo, new BigDecimal("20000"),
                        avance, "USD", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, objetivo, new BigDecimal("30000"),
                        avance, "PEN", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, objetivo, new BigDecimal("30000"),
                        avance, "USD", new BigDecimal("5000"), false));

        for (DefinirRocaMaestraCommand cambio : cambios) {
            assertThatThrownBy(() -> service.definir(cambio)).isInstanceOf(RocaMaestraFijaException.class);
        }
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    // ---------------------------------------------------------------------------------------
    // D-284 (issue #20): el objetivo queda fijo cuando el MAPA esta activo, no cuando se definio
    // ---------------------------------------------------------------------------------------

    private static final String OBJETIVO_DE_PLAN = "Facturar 30.000 USD en contratos high-ticket";
    private static final String OBJETIVO_DEL_MAPA = "Facturar 45.000 USD con tres clientes recurrentes";

    @Test
    @DisplayName("D-284: Mapa sin activar, la activacion escribe otro objetivo: reemplaza al de Plan, misma roca")
    void elMapaSinActivarReemplazaAlObjetivoDePlan() {
        UserId id = traineeActivo();
        mapaActivo(id, false);
        RocaMaestraId yaExistente = yaDefinida(id, new BigDecimal("19500.00"));

        RocaMaestra guardada = service.definir(
                comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000")));

        assertThat(guardada.id()).as("misma roca, no una segunda").isEqualTo(yaExistente);
        assertThat(guardada.objetivo()).isEqualTo(OBJETIVO_DEL_MAPA);
        assertThat(guardada.meta().objetivo()).isEqualByComparingTo("45000");
        assertThat(guardada.meta().avance()).isEqualByComparingTo("12000");
        assertThat(guardada.creadoEn()).isEqualTo(AYER);
        assertThat(guardada.actualizadoEn()).isEqualTo(AHORA);
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("D-284: lo mismo con el bloqueo encendido: el Mapa sin activar tambien reemplaza")
    void elMapaReemplazaTambienConElBloqueoEncendido() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, false);
        yaDefinida(id, new BigDecimal("19500.00"));

        RocaMaestra guardada = service.definir(
                comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000")));

        assertThat(guardada.objetivo()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("D-284: Mapa sin activar y sin objetivo previo: la activacion lo crea")
    void elMapaSinActivarCreaLaRoca() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, false);
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        RocaMaestra guardada = service.definir(
                comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000")));

        assertThat(guardada.id().value()).isEqualTo(ID_GENERADO);
        assertThat(guardada.objetivo()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("D-284: reintento de la activacion con el Mapa sin activar: la misma definicion no toca nada")
    void elReintentoDeLaActivacionEsIdempotente() {
        UserId id = traineeActivo();
        mapaActivo(id, false);
        yaDefinida(id, new BigDecimal("19500.00"));

        RocaMaestra guardada = service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO,
                OBJETIVO_DE_PLAN, new BigDecimal("30000"), new BigDecimal("19500"), "USD", null, true));

        assertThat(guardada.actualizadoEn()).as("nada cambio").isEqualTo(AYER);
    }

    @Test
    @DisplayName("D-284: con el Mapa ACTIVO el objetivo sigue fijo aunque el pedido diga que viene del Mapa")
    void conElMapaActivoSigueFijoVengaDeDondeVenga() {
        UserId id = traineeActivo();
        mapaActivo(id, true);
        yaDefinida(id, new BigDecimal("19500.00"));

        assertThatThrownBy(() -> service.definir(comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"),
                new BigDecimal("12000")))).isInstanceOf(RocaMaestraFijaException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("D-284: con el Mapa activo y sin roca (caso raro) se crea, como siempre")
    void conElMapaActivoSinRocaSeCrea() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, true);
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        assertThat(service.definir(comando(id)).id().value()).isEqualTo(ID_GENERADO);
    }

    @Test
    @DisplayName("D-284: bloqueo apagado (default), pedido de un APK que no manda el origen: reemplaza, como el Mapa")
    void sinElBloqueoUnPedidoSinOrigenReemplazaMientrasElMapaNoEstaActivo() {
        UserId id = traineeActivo();
        mapaActivo(id, false);
        yaDefinida(id, new BigDecimal("19500.00"));
        DefinirRocaMaestraCommand delApkAnterior = new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO,
                OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000"), "USD", new BigDecimal("12000"),
                false);

        assertThat(service.definir(delApkAnterior).objetivo()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("D-284: bloqueo encendido, Mapa sin activar: definir un objetivo desde fuera del Mapa es MAPA_PENDIENTE")
    void conElBloqueoNoSePuedeDefinirDesdeFueraDelMapa() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, false);
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definir(comando(id)))
                .isInstanceOf(MapaPendienteException.class)
                .hasMessageContaining("Primero completa tu Mapa de Renacimiento");
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("D-284: bloqueo encendido, Mapa sin activar: tampoco se cambia el objetivo que ya habia")
    void conElBloqueoNoSeCambiaElObjetivoDePlanDesdeFueraDelMapa() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, false);
        yaDefinida(id, new BigDecimal("19500.00"));

        assertThatThrownBy(() -> service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO,
                OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000"), "USD", null, false)))
                .isInstanceOf(MapaPendienteException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("D-284: bloqueo encendido, Mapa sin activar: repetir la misma definicion (solo otro avance) sigue valiendo")
    void conElBloqueoRepetirLaMismaDefinicionNoCambiaElObjetivo() {
        service = servicio(true);
        UserId id = traineeActivo();
        mapaActivo(id, false);
        yaDefinida(id, new BigDecimal("10000.00"));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.meta().avance()).isEqualByComparingTo("19500");
        assertThat(guardada.objetivo()).isEqualTo(OBJETIVO_DE_PLAN);
    }

    /**
     * El caso exacto del issue #20, en el orden real de la app: Plan define, despues el Mapa escribe las
     * rocas (todavia sin activar), reintenta, la app marca el Mapa activo, y desde ahi el objetivo es fijo.
     * Con un almacen en memoria para que cada paso vea lo que dejo el anterior.
     */
    @Test
    @DisplayName("issue #20: Plan define, el Mapa reemplaza y reintenta, el Mapa se activa y el objetivo queda fijo")
    void elCasoDelIssue20DePunta() {
        UserId id = traineeActivo();
        AtomicReference<RocaMaestra> almacen = new AtomicReference<>();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO))
                .thenAnswer(inv -> Optional.ofNullable(almacen.get()));
        when(guardarRocaMaestraPort.guardar(any())).thenAnswer(inv -> {
            almacen.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(estadoDelMapaPort.activo(id)).thenReturn(false, false, false, true, true);

        service.definir(comando(id));
        assertThat(almacen.get().objetivo()).as("Plan define antes del Mapa").isEqualTo(OBJETIVO_DE_PLAN);

        RocaMaestra delMapa = service.definir(
                comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000")));
        assertThat(delMapa.objetivo()).as("la activacion del Mapa ya no choca").isEqualTo(OBJETIVO_DEL_MAPA);

        service.definir(comandoDelMapa(id, OBJETIVO_DEL_MAPA, new BigDecimal("45000"), new BigDecimal("12000")));
        assertThat(almacen.get().objetivo()).as("el reintento deja lo mismo").isEqualTo(OBJETIVO_DEL_MAPA);

        assertThatThrownBy(() -> service.definir(comando(id))).as("con el Mapa activo vuelve a ser fijo")
                .isInstanceOf(RocaMaestraFijaException.class);
        assertThat(almacen.get().objetivo()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("un objetivo sin numeros se guarda sin meta, no con una meta en cero")
    void definirSinMetaGuardaSinMeta() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.RELACIONES)).thenReturn(Optional.empty());

        service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.RELACIONES,
                "Recuperar la confianza con mi hijo", null, null, null, null, false));

        ArgumentCaptor<RocaMaestra> guardada = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort).guardar(guardada.capture());
        assertThat(guardada.getValue().tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("media meta no vale: sin unidad el numero no se puede ni mostrar")
    void elComandoRechazaUnaMetaIncompleta() {
        UserId id = actor();

        assertThatThrownBy(() -> new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Facturar",
                new BigDecimal("30000"), new BigDecimal("0"), null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * E-169: un MENTOR que activo su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de rechazo, y se construye sobre su mismo fixture para que
     * la unica diferencia sea el dato que importa. Antes de esto,
     * {@code POST /api/v1/mentor/activate-tracking} inscribia al staff y despues el guard lo
     * echaba: la inscripcion estaba construida y el uso prohibido.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progresoActivado(RolParticipante.MENTOR, false)));

        GuardDeRol.noRechaza(() -> service.misRocasMaestras(id), "Solo un aprendiz opera sus propias rocas");
    }
}
