package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase.CerrarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase.IniciarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.SolicitarUrlAdjuntoRachaUseCase.SolicitarUrlAdjuntoRachaCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadRachaSinCelularPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveRachaSinCelularPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.EstadoRacha;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RachaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, iniciar() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadRachaSinCelularPort loadRachaPort;
    @Mock
    private SaveRachaSinCelularPort saveRachaPort;
    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private RegistrarEvidenciaPort registrarEvidenciaPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;
    /** Ver comentario equivalente en RegistroServiceTest: no necesita stubbing. */
    @Mock
    private PlatformTransactionManager transactionManager;

    private RachaService service;

    @BeforeEach
    void setUp() {
        service = new RachaService(loadRachaPort, saveRachaPort, loadRegistroPort, saveRegistroPort, loadHabitoPort,
                progresoPort, ajustarPuntosPort, registrarEvidenciaPort, almacenamientoPort, events, CLOCK,
                idGenerator, transactionManager);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(saveRachaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(registrarEvidenciaPort.registrar(any()))
                .thenReturn(new EvidenciaRegistrada(UUID.randomUUID(), EstadoValidacion.PENDIENTE));
    }

    private static Habito habitoSinCelular() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Dia sin celular", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        // claveSistema no se puede fijar por el factory publico (no expone ese setter) — se usa
        // reflexion minima via rehydrate en el test que lo necesita.
    }

    private static Habito habitoSinCelularConClave() {
        Habito h = habitoSinCelular();
        return Habito.rehydrate(h.id(), h.ambito(), null, h.titulo(), null, h.tipo(), h.categoriaClave(), null,
                RachaService.CLAVE_SISTEMA_SIN_CELULAR, h.exigenciaEvidencia(), false, false, false, null, null,
                null, null, true, CLOCK.now(), CLOCK.now());
    }

    /** Evidencia minima valida (TEXTO) para cerrar — el cierre va siempre con evidencia (Hueco #13). */
    private static CerrarRachaCommand cerrarConEvidencia(UserId actorId) {
        return new CerrarRachaCommand(actorId, TipoEvidencia.TEXTO, null, null, "cerre mi racha", null);
    }

    @Test
    void iniciarRechazaHabitoQueNoEsDiaSinCelular() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito otroHabito = habitoSinCelular(); // sin claveSistema
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                otroHabito.id(), LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(otroHabito.id())).thenReturn(Optional.of(otroHabito));

        assertThatThrownBy(() -> service.iniciar(new IniciarRachaCommand(participante, registro.id(), 24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iniciarRechazaSiYaHayUnaRachaActiva() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habito.id(), LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDe(participante)).thenReturn(Optional.of(
                RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante, registro.id(), 24,
                        CLOCK.now())));

        assertThatThrownBy(() -> service.iniciar(new IniciarRachaCommand(participante, registro.id(), 24)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cerrarRechazaActorSinRachaActiva() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cerrar(cerrarConEvidencia(participante)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void cerrarSuspendidoRechazado() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.cerrar(cerrarConEvidencia(participante)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void cerrarConCicloCompletoOtorgaDiezPuntosYCompletaElRegistro() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habito.id(), LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RachaSinCelular racha = RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante,
                registro.id(), 24, CLOCK.now().minus(Duration.ofHours(24)));

        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.of(racha));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(ajustarPuntosPort.ajustar(any(), any(), anyInt(), any()))
                .thenReturn(new ResumenAjustePuntos(participante, 10, 110));

        RachaSinCelular resultado = service.cerrar(cerrarConEvidencia(participante));

        assertThat(resultado.estado()).isEqualTo(EstadoRacha.COMPLETADA);
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        verify(ajustarPuntosPort).ajustar(eq(participante), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    void cerrarRegistraLaEvidenciaColgadaDelRegistroQueArrancoLaRacha() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habito.id(), LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RachaSinCelular racha = RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante,
                registro.id(), 24,
                CLOCK.now().minus(Duration.ofHours(4))); // hito parcial, no ciclo completo

        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.of(racha));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));

        service.cerrar(new CerrarRachaCommand(participante, TipoEvidencia.TEXTO, null, null, "una nota", null));

        verify(registrarEvidenciaPort).registrar(new RegistrarEvidenciaComando(participante,
                new DestinoEvidencia.RegistroHabito(registro.id().value()), TipoEvidencia.TEXTO, null, null,
                "una nota", null, null, null, false, CLOCK.now()));
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    @Test
    void solicitarUrlDevuelveUrlFirmadaParaLaRachaActiva() {
        UserId participante = UserId.of(UUID.randomUUID());
        RachaSinCelular racha = RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante,
                RegistroHabitoId.of(UUID.randomUUID()), 24, CLOCK.now());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDe(participante)).thenReturn(Optional.of(racha));
        when(almacenamientoPort.firmarSubida(any(), any(), any())).thenReturn(URI.create("https://example.com/x"));

        var url = service.solicitarUrl(new SolicitarUrlAdjuntoRachaCommand(participante, "image/jpeg"));

        assertThat(url.bucket()).isEqualTo(RachaService.BUCKET_DIA_SIN_CELULAR);
        assertThat(url.ruta()).contains(racha.id().toString());
        assertThat(url.url()).isEqualTo(URI.create("https://example.com/x"));
    }

    @Test
    void solicitarUrlSinRachaActivaLanza() {
        UserId participante = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDe(participante)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.solicitarUrl(new SolicitarUrlAdjuntoRachaCommand(participante, "image/jpeg")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void romperNuncaPenalizaPuntos() {
        UserId participante = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habito.id(), LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RachaSinCelular racha = RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante,
                registro.id(), 24, CLOCK.now().minus(Duration.ofHours(2)));

        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRachaPort.activaDeParaEscritura(participante)).thenReturn(Optional.of(racha));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));

        service.romper(new com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase.RomperRachaCommand(
                participante, "me distraje"));

        assertThat(racha.estado()).isEqualTo(EstadoRacha.ROTA);
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE); // liberado, es de hoy
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    /**
     * C-6 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): la racha 2 falla
     * al guardar (fila corrupta simulada); eso no debe impedir que las rachas 1 y 3, vencidas
     * en el mismo barrido, terminen EXPIRADA con su registro liberado.
     */
    @Test
    @DisplayName("expirarVencidas(): una racha que falla al guardar no tumba el barrido de las demas")
    void expirarVencidasAislaLaRachaQueFalla() {
        UserId p1 = UserId.of(UUID.randomUUID());
        UserId p2 = UserId.of(UUID.randomUUID());
        UserId p3 = UserId.of(UUID.randomUUID());
        Habito habito = habitoSinCelularConClave();
        RegistroHabito registro1 = registroEnCursoDe(p1, habito.id());
        RegistroHabito registro2 = registroEnCursoDe(p2, habito.id());
        RegistroHabito registro3 = registroEnCursoDe(p3, habito.id());
        RachaSinCelular racha1 = rachaVencidaDe(p1, registro1.id());
        RachaSinCelular racha2 = rachaVencidaDe(p2, registro2.id());
        RachaSinCelular racha3 = rachaVencidaDe(p3, registro3.id());

        when(loadRachaPort.activasDe(List.of(p1, p2, p3))).thenReturn(List.of(racha1, racha2, racha3));
        // lenient: la racha que falla (racha2) revienta ANTES de llegar a liberarRegistro, asi
        // que su progresoPort/loadRegistroPort nunca se invocan — no es un descuido del test,
        // es justamente lo que C-6 aisla (esa fila queda a medio camino, sin tocar las demas).
        for (UserId p : List.of(p1, p2, p3)) {
            lenient().when(progresoPort.deParticipante(p)).thenReturn(
                    Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        }
        lenient().when(loadRegistroPort.byId(registro1.id())).thenReturn(Optional.of(registro1));
        lenient().when(loadRegistroPort.byId(registro2.id())).thenReturn(Optional.of(registro2));
        lenient().when(loadRegistroPort.byId(registro3.id())).thenReturn(Optional.of(registro3));
        when(saveRachaPort.save(racha2)).thenThrow(new IllegalStateException("fila corrupta simulada"));

        int expiradas = service.expirarVencidas(List.of(p1, p2, p3));

        assertThat(expiradas).as("solo racha1 y racha3 se guardaron bien").isEqualTo(2);
        assertThat(racha1.estado()).isEqualTo(EstadoRacha.EXPIRADA);
        assertThat(racha3.estado()).isEqualTo(EstadoRacha.EXPIRADA);
        assertThat(registro1.estado()).isEqualTo(EstadoRegistro.EXPIRADO); // no es de hoy (CLOCK)
        assertThat(registro3.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }

    private RegistroHabito registroEnCursoDe(UserId participante, HabitoId habitoId) {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante,
                habitoId, LocalDate.of(2026, 8, 22), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        return registro;
    }

    /** Vencida hace rato: iniciada 48h atras, muy por encima de las 24h+3h de plazo. */
    private static RachaSinCelular rachaVencidaDe(UserId participante, RegistroHabitoId registroId) {
        return RachaSinCelular.iniciar(RachaSinCelularId.of(UUID.randomUUID()), participante, registroId, 24,
                CLOCK.now().minus(Duration.ofHours(48)));
    }
}
