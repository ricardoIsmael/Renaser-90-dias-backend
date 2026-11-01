package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarObjetivosDelDiagnosticoPort;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarObjetivosDelDiagnosticoPort.ObjetivoDelDiagnostico;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.OrigenDelObjetivo;
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
    private static final String OBJETIVO = "Facturar 30.000 USD en contratos high-ticket";

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private GuardarRocaMaestraPort guardarRocaMaestraPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private ConsultarObjetivosDelDiagnosticoPort diagnosticoPort;
    @Mock
    private IdGenerator idGenerator;

    private RocaMaestraService service;

    @BeforeEach
    void setUp() {
        service = new RocaMaestraService(loadRocaMaestraPort, guardarRocaMaestraPort, progresoPort, diagnosticoPort, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(guardarRocaMaestraPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(diagnosticoPort.de(any())).thenReturn(List.of());
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
        return new DefinirRocaMaestraCommand(actor, EjeObjetivo.TRABAJO, OBJETIVO, new BigDecimal("30000"), new BigDecimal("19500"), "USD", null, false);
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
        RocaMaestra maestra = RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), id, EjeObjetivo.CUERPO, "objetivo", null, AYER);
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
    @DisplayName("spec 026: sin Mapa ni diagnóstico, el objetivo se escribe directo en Plan: se crea con la identidad del generador, fijo y de origen PLAN")
    void definirCreaCuandoNoExiste() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id().value()).isEqualTo(ID_GENERADO);
        assertThat(guardada.participanteId()).isEqualTo(id);
        assertThat(guardada.eje()).isEqualTo(EjeObjetivo.TRABAJO);
        assertThat(guardada.creadoEn()).isEqualTo(AHORA);
        assertThat(guardada.meta().porcentaje()).isEqualTo(65);
        assertThat(guardada.origen()).isEqualTo(OrigenDelObjetivo.PLAN);
        assertThat(guardada.estaPorConfirmar()).isFalse();
    }

    private RocaMaestraId yaDefinida(UserId id, BigDecimal avance) {
        RocaMaestraId yaExistente = RocaMaestraId.of(UUID.randomUUID());
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(
                RocaMaestra.rehydrate(yaExistente, id, EjeObjetivo.TRABAJO, OBJETIVO,
                        new MetaCuantitativa(new BigDecimal("30000.00"), avance, "USD", null), AYER, AYER)));
        return yaExistente;
    }

    @Test
    @DisplayName("D-234: la misma definicion otra vez se acepta y deja la roca igual")
    void definirLoMismoEsIdempotente() {
        UserId id = traineeActivo();
        RocaMaestraId yaExistente = yaDefinida(id, new BigDecimal("19500.00"));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id()).as("misma roca, no una segunda").isEqualTo(yaExistente);
        assertThat(guardada.actualizadoEn()).as("nada cambio").isEqualTo(AYER);
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("D-234: si solo cambia el avance, se registra el progreso sobre la misma roca")
    void definirConOtroAvanceRegistraElProgreso() {
        UserId id = traineeActivo();
        RocaMaestraId yaExistente = yaDefinida(id, new BigDecimal("10000.00"));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id()).isEqualTo(yaExistente);
        assertThat(guardada.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(guardada.actualizadoEn()).isEqualTo(AHORA);
        assertThat(guardada.meta().avance()).isEqualByComparingTo("19500");
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("D-234: cambiar objetivo, meta, unidad o linea base de una roca ya definida se rechaza sin guardar, haya o no Mapa")
    void definirRechazaCambiarLoFijo() {
        UserId id = traineeActivo();
        yaDefinida(id, new BigDecimal("19500.00"));
        BigDecimal avance = new BigDecimal("19500");
        List<DefinirRocaMaestraCommand> cambios = List.of(
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Otro objetivo", new BigDecimal("30000"), avance, "USD", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, OBJETIVO, new BigDecimal("20000"), avance, "USD", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, OBJETIVO, new BigDecimal("30000"), avance, "PEN", null, false),
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, OBJETIVO, new BigDecimal("30000"), avance, "USD", new BigDecimal("5000"), false),
                // Lo que mandaba la activacion del Mapa: ya no reemplaza nada (spec 026)
                new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Facturar 45.000 USD", new BigDecimal("45000"), avance, "USD", null, true));

        for (DefinirRocaMaestraCommand cambio : cambios) {
            assertThatThrownBy(() -> service.definir(cambio)).isInstanceOf(RocaMaestraFijaException.class);
        }
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("un objetivo sin numeros se guarda sin meta, no con una meta en cero")
    void definirSinMetaGuardaSinMeta() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.RELACIONES)).thenReturn(Optional.empty());

        service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.RELACIONES, "Recuperar la confianza con mi hijo", null, null, null, null, false));

        ArgumentCaptor<RocaMaestra> guardada = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort).guardar(guardada.capture());
        assertThat(guardada.getValue().tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("media meta no vale: sin unidad el numero no se puede ni mostrar")
    void elComandoRechazaUnaMetaIncompleta() {
        UserId id = actor();

        assertThatThrownBy(() -> new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Facturar", new BigDecimal("30000"), new BigDecimal("0"), null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------------------------
    // Spec 026: los objetivos que nacen del diagnostico llegan «por confirmar»
    // ---------------------------------------------------------------------------------------

    private RocaMaestra delDiagnostico(UserId id, EjeObjetivo eje, String texto) {
        return RocaMaestra.delDiagnostico(RocaMaestraId.of(UUID.randomUUID()), id, eje, texto, AYER);
    }

    @Test
    @DisplayName("al leer sus rocas se le crean las que nacieron de su diagnostico y todavia no existen, por confirmar")
    void leerMaterializaLasDelDiagnostico() {
        UserId id = traineeActivo();
        RocaMaestra propia = RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), id, EjeObjetivo.CUERPO, "Mi objetivo de siempre", null, AYER);
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of(propia));
        when(diagnosticoPort.de(id)).thenReturn(List.of(new ObjetivoDelDiagnostico(EjeObjetivo.CUERPO, "El del diagnostico"),
                new ObjetivoDelDiagnostico(EjeObjetivo.TRABAJO, "Facturar 15 mil"), new ObjetivoDelDiagnostico(EjeObjetivo.RELACIONES, "Hablar sin evitar")));

        service.misRocasMaestras(id);

        ArgumentCaptor<RocaMaestra> creadas = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort, org.mockito.Mockito.times(2)).guardar(creadas.capture());
        assertThat(creadas.getAllValues()).extracting(RocaMaestra::eje).containsExactly(EjeObjetivo.TRABAJO, EjeObjetivo.RELACIONES);
        assertThat(creadas.getAllValues()).allSatisfy(r -> {
            assertThat(r.origen()).isEqualTo(OrigenDelObjetivo.DIAGNOSTICO);
            assertThat(r.estaPorConfirmar()).isTrue();
            assertThat(r.tieneMeta()).isFalse();
        });
    }

    @Test
    @DisplayName("la meta medible que declaro en el diagnostico viaja con el objetivo")
    void laMetaDelDiagnosticoViajaConElObjetivo() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of());
        when(diagnosticoPort.de(id)).thenReturn(List.of(new ObjetivoDelDiagnostico(EjeObjetivo.CUERPO, "Bajar de peso",
                new ConsultarObjetivosDelDiagnosticoPort.MetaDelDiagnostico(new BigDecimal("85"), new BigDecimal("92"), "kg"))));

        service.misRocasMaestras(id);

        ArgumentCaptor<RocaMaestra> creada = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort).guardar(creada.capture());
        assertThat(creada.getValue().meta().objetivo()).isEqualByComparingTo("85");
        assertThat(creada.getValue().meta().lineaBase()).isEqualByComparingTo("92");
        assertThat(creada.getValue().meta().unidad()).isEqualTo("kg");
        assertThat(creada.getValue().estaPorConfirmar()).isTrue();
    }

    @Test
    @DisplayName("una meta que no es meta (igual a donde parte, o una unidad larga) no impide el objetivo: nace en palabras")
    void unaMetaInvalidaNoImpideElObjetivo() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of());
        when(diagnosticoPort.de(id)).thenReturn(List.of(
                new ObjetivoDelDiagnostico(EjeObjetivo.CUERPO, "Sostener mi peso",
                        new ConsultarObjetivosDelDiagnosticoPort.MetaDelDiagnostico(new BigDecimal("85"), new BigDecimal("85"), "kg")),
                new ObjetivoDelDiagnostico(EjeObjetivo.TRABAJO, "Facturar mas",
                        new ConsultarObjetivosDelDiagnosticoPort.MetaDelDiagnostico(new BigDecimal("15000"), new BigDecimal("10000"), "soles peruanos de facturacion"))));

        service.misRocasMaestras(id);

        ArgumentCaptor<RocaMaestra> creadas = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort, org.mockito.Mockito.times(2)).guardar(creadas.capture());
        assertThat(creadas.getAllValues()).allSatisfy(r -> assertThat(r.tieneMeta()).isFalse());
    }

    @Test
    @DisplayName("con las tres rocas ya creadas ni siquiera se le pregunta al diagnostico")
    void conLasTresNoSePregunta() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of(delDiagnostico(id, EjeObjetivo.CUERPO, "a"),
                delDiagnostico(id, EjeObjetivo.TRABAJO, "b"), delDiagnostico(id, EjeObjetivo.RELACIONES, "c")));

        assertThat(service.misRocasMaestras(id)).hasSize(3);
        verify(diagnosticoPort, never()).de(any());
    }

    @Test
    @DisplayName("si otro pedido ya habia creado la roca, no se rompe: se vuelve a leer lo que dejo")
    void laCarreraNoRompe() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of());
        when(diagnosticoPort.de(id)).thenReturn(List.of(new ObjetivoDelDiagnostico(EjeObjetivo.TRABAJO, "Facturar")));
        when(guardarRocaMaestraPort.guardar(any())).thenThrow(new IllegalStateException("UNIQUE (participante_id, eje)"));

        assertThat(service.misRocasMaestras(id)).isEmpty();
    }

    @Test
    @DisplayName("Q2-A: el objetivo por confirmar se puede reescribir UNA vez; al confirmarlo queda fijo")
    void elPorConfirmarSeReescribeUnaVez() {
        UserId id = traineeActivo();
        RocaMaestra porConfirmar = delDiagnostico(id, EjeObjetivo.TRABAJO, "Facturar 15 mil");
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(porConfirmar));

        RocaMaestra confirmada = service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Facturar 20 mil con tres clientes", null, null, null,
                null, false));

        assertThat(confirmada.id()).as("misma roca: lo que cuelga de ella sigue apuntando").isEqualTo(porConfirmar.id());
        assertThat(confirmada.objetivo()).isEqualTo("Facturar 20 mil con tres clientes");
        assertThat(confirmada.estaPorConfirmar()).isFalse();
        assertThat(confirmada.confirmadaEn()).isEqualTo(AHORA);
        assertThat(confirmada.origen()).isEqualTo(OrigenDelObjetivo.DIAGNOSTICO);
        assertThat(confirmada.creadoEn()).isEqualTo(AYER);

        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(confirmada));
        assertThatThrownBy(() -> service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Otra cosa", null, null, null, null, false)))
                .isInstanceOf(RocaMaestraFijaException.class);
    }

    @Test
    @DisplayName("confirmar sin cambiar nada tambien la deja fija")
    void confirmarSinCambiar() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.CUERPO))
                .thenReturn(Optional.of(delDiagnostico(id, EjeObjetivo.CUERPO, "Entrenar cuatro dias")));

        RocaMaestra confirmada = service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.CUERPO, "Entrenar cuatro dias", null, null, null, null, false));

        assertThat(confirmada.objetivo()).isEqualTo("Entrenar cuatro dias");
        assertThat(confirmada.estaPorConfirmar()).isFalse();
    }

    @Test
    @DisplayName("las personas que ya existian siguen igual: su objetivo ya esta confirmado y fijo")
    void lasDeSiempreSonConfirmadas() {
        RocaMaestra deSiempre = RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actor(), EjeObjetivo.CUERPO, "x", null, AYER, AYER);

        assertThat(deSiempre.origen()).isEqualTo(OrigenDelObjetivo.PLAN);
        assertThat(deSiempre.estaPorConfirmar()).isFalse();
    }

    /**
     * E-169: un MENTOR que activo su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de rechazo, y se construye sobre su mismo fixture para que la unica diferencia sea el dato que importa.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progresoActivado(RolParticipante.MENTOR, false)));

        GuardDeRol.noRechaza(() -> service.misRocasMaestras(id), "Solo un aprendiz opera sus propias rocas");
    }
}
