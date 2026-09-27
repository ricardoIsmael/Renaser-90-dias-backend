package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaClaseDiaria;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.politica.PoliticaSantuario;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CLAVE_SISTEMA_DAILY_CLASS;
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
class RegistroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, generar() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private com.renaser.os.community.api.PublicacionMuroFinder publicacionMuroFinder;
    @Mock
    private com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;
    /**
     * Sin stubbing: {@code TransactionTemplate} funciona igual con un mock "vacio"
     * (getTransaction/commit/rollback no hacen nada) porque estos tests no ejercitan
     * transacciones reales — lo que importa aca es que el callback de
     * {@code transaccionPropia.executeWithoutResult(...)} se siga ejecutando (ver C-6).
     */
    @Mock
    private PlatformTransactionManager transactionManager;

    private RegistroService service;

    @BeforeEach
    void setUp() {
        // Las politicas reales, no mocks: son funciones puras sin dependencias, y usarlas
        // tal cual mantiene el test fiel al comportamiento de produccion (el rechazo de
        // BLOQUEO que antes estaba hardcodeado en el servicio ahora lo aporta esta).
        service = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder, loadDesbloqueoPort, events,
                CLOCK, idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                transactionManager);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveRegistroPort.insertarSiNoExiste(any())).thenReturn(true);
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static Habito habitoCheckbox() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    /**
     * El habito real POST DIARIO EN COMUNIDAD: CHECKBOX (igual que otros quince del catalogo)
     * y con la {@code claveSistema} que le pone V24. Se arma con {@code rehydrate} y no con
     * {@code crearDeSistema} porque esa factoria deja la clave en null a proposito.
     */
    private static Habito habitoPostDiarioComunidad() {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null,
                "POST DIARIO EN COMUNIDAD", null, TipoHabito.CHECKBOX, "CONSCIENCIA", "COMMUNITY_POST",
                PoliticaPostDiarioComunidad.CLAVE_SISTEMA, ExigenciaEvidencia.OPCIONAL, false, true, false, false,
                null, null, null, null, true, CLOCK.now(), CLOCK.now());
    }

    /** El habito real CLASE DIARIA: CHECKBOX con {@code clave_sistema = 'DAILY_CLASS'} (V4). */
    private static Habito habitoClaseDiaria() {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "Clase diaria", null,
                TipoHabito.CHECKBOX, "MENTE", "READING", CLAVE_SISTEMA_DAILY_CLASS, ExigenciaEvidencia.OPCIONAL,
                false, false, false, false, null, null, null, null, true, CLOCK.now(), CLOCK.now());
    }

    private RegistroHabito registroPendiente(UserId participanteId, HabitoId habitoId) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habitoId,
                LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // CLASE DIARIA (E-120): el contrato dice que sin resumen el habito NO queda completado.
    // Los dos tests de abajo FALLAN contra el codigo anterior a PoliticaClaseDiaria — antes
    // DAILY_CLASS era un CHECKBOX cualquiera y la ruta generica lo cerraba sin pedir nada.
    // ────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CLASE DIARIA: la ruta generica (y la herramienta del agente) no puede cerrarla ni pagarla")
    void claseDiariaNoSeCierraConElGestoGenerico() {
        UserId dueno = participante();
        Habito habito = habitoClaseDiaria();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), "texto suelto sin pasar por la clase", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumen");

        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE);
        verify(saveRegistroPort, never()).save(any());
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("CLASE DIARIA: el gesto propio (POST /classroom/clase-diaria) si la cierra y paga")
    void claseDiariaSeCierraConSuGestoPropio() {
        UserId dueno = participante();
        Habito habito = habitoClaseDiaria();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteHabitoYFecha(dueno, habito.id(), registro.fechaEjecucion())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));

        RegistroHabito resultado = service.completar(new CompletarRegistroCommand(dueno, registro.id(),
                "Entendi que la disciplina se construye a diario", null, GestoCompletar.PROPIO_DEL_HABITO));

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(resultado.respuestaTexto()).isEqualTo("Entendi que la disciplina se construye a diario");
        verify(ajustarPuntosPort).ajustar(eq(dueno), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    @DisplayName("consultar(): un actor distinto del participante recibe NotAuthorizedException (CLAUDE.MD §0.3)")
    void consultarRechazaActorAjeno() {
        UserId actor = participante();
        UserId otro = participante();
        assertThatThrownBy(() -> service.consultar(actor, otro, LocalDate.now()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarDelegaAlPuertoParaElPropioParticipante() {
        UserId participante = participante();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadRegistroPort.porParticipanteYFecha(participante, LocalDate.of(2026, 8, 24)))
                .thenReturn(List.of());
        List<RegistroHabito> resultado = service.consultar(participante, participante, LocalDate.of(2026, 8, 24));
        assertThat(resultado).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // POST DIARIO EN COMUNIDAD (pedido del dueno, 2026-09-04): solo se completa si publico.
    // Los cuatro tests de abajo FALLAN contra el codigo anterior a PoliticaPostDiarioComunidad
    // — antes este habito era un CHECKBOX cualquiera y se completaba siempre.
    // ────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST DIARIO: sin publicacion en el Muro no se completa, no da puntos y el registro sigue PENDIENTE")
    void postDiarioSinPublicacionNoSeCompleta() {
        UserId dueno = participante();
        Habito habito = habitoPostDiarioComunidad();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(publicacionMuroFinder.publicoEntre(eq(dueno), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Muro");

        // Lo que de verdad importa: no se le pago ni se le movio el estado por decir que publico.
        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE);
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
        verify(saveRegistroPort, never()).save(any());
    }

    @Test
    @DisplayName("POST DIARIO: con publicacion ese dia se completa como cualquier otro habito")
    void postDiarioConPublicacionSeCompleta() {
        UserId dueno = participante();
        Habito habito = habitoPostDiarioComunidad();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteHabitoYFecha(dueno, habito.id(), registro.fechaEjecucion())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(publicacionMuroFinder.publicoEntre(eq(dueno), any(), any())).thenReturn(true);

        RegistroHabito resultado = service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null));

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
    }

    /**
     * El test que hubiera atrapado E-91 en esta regla (regla 02): el reloj a las 02:00 UTC cae
     * el dia ANTERIOR en Lima (UTC-5). Si la ventana se calculara en UTC — o peor, con
     * {@code clock.today()} — se le contaria al aprendiz el post de otro dia. Se verifican los
     * dos instantes exactos, no que "se llamo al puerto".
     */
    @Test
    @DisplayName("POST DIARIO: el dia se abre y cierra en la zona del participante, no en UTC")
    void postDiarioMideElDiaEnLaZonaDelParticipante() {
        UserId dueno = participante();
        Habito habito = habitoPostDiarioComunidad();
        // El registro es del 24/08; el reloj del test esta en otro instante a proposito: lo que
        // manda es la fecha de ejecucion del registro, no "ahora".
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(publicacionMuroFinder.publicoEntre(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        // 24/08 00:00 en Lima = 24/08 05:00Z; 25/08 00:00 en Lima = 25/08 05:00Z.
        verify(publicacionMuroFinder).publicoEntre(dueno, Instant.parse("2026-08-24T05:00:00Z"),
                Instant.parse("2026-08-25T05:00:00Z"));
    }

    @Test
    @DisplayName("un habito sin regla propia no consulta el Muro (la consulta es perezosa, no se paga de gratis)")
    void habitoComunNoConsultaElMuro() {
        UserId dueno = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteHabitoYFecha(dueno, habito.id(), registro.fechaEjecucion())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, false)));

        service.completar(new CompletarRegistroCommand(dueno, registro.id(), null, null));

        verify(publicacionMuroFinder, never()).publicoEntre(any(), any(), any());
    }

    @Test
    @DisplayName("completar(): actor distinto del dueno del registro -> NotAuthorizedException")
    void completarRechazaActorAjeno() {
        UserId dueno = participante();
        UserId otro = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(otro, registro.id(), null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    /**
     * D-97: antes esta prueba afirmaba 0 puntos y ninguna llamada a AjustarPuntosPort (fiel a
     * applyHabitAward del repo viejo). El dueno definio lo contrario: sin horario, la hora de la
     * accion es el ancla — siempre a tiempo, puntaje completo. Es el caso de DESPERTAR.
     */
    @Test
    @DisplayName("completar sin horario configurado: la hora de la accion es el ancla, 10 puntos (D-97)")
    void completarSinHorarioOtorgaPuntajeCompleto() {
        UserId dueno = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteHabitoYFecha(dueno, habito.id(), registro.fechaEjecucion())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false, false)));

        RegistroHabito resultado = service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), "listo", null));

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(ajustarPuntosPort).ajustar(eq(dueno), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    @DisplayName("completar a tiempo con horario configurado: otorga 10 puntos via AjustarPuntosPort, motivo HABIT_COMPLETED")
    void completarATiempoOtorgaDiezPuntos() {
        UserId dueno = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        var horario = com.renaser.os.habits.domain.model.horario.HorarioHabito.crear(
                HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS, java.time.LocalTime.of(6,
                        0), java.time.LocalTime.of(23, 0), CLOCK.now());

        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(horario));
        when(loadPreferenciaPort.porParticipanteHabitoYFecha(dueno, habito.id(), registro.fechaEjecucion())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false, false)));
        when(ajustarPuntosPort.ajustar(any(), any(), anyInt(), any()))
                .thenReturn(new ResumenAjustePuntos(dueno, 10, 110));

        RegistroHabito resultado = service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null));

        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(ajustarPuntosPort).ajustar(eq(dueno), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    void completarUnHabitoBloqueoRechazado() {
        UserId dueno = participante();
        Habito bloqueo = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Santuario", TipoHabito.BLOQUEO, "MENTE",
                com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        RegistroHabito registro = registroPendiente(dueno, bloqueo.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(bloqueo.id())).thenReturn(Optional.of(bloqueo));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false, false)));

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expirarPendientesAnterioresADelegaYCuentaLosExpirados() {
        RegistroHabito r1 = registroPendiente(participante(), HabitoId.of(UUID.randomUUID()));
        RegistroHabito r2 = registroPendiente(participante(), HabitoId.of(UUID.randomUUID()));
        when(loadRegistroPort.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE, LocalDate.of(2026, 8, 24)))
                .thenReturn(List.of(r1, r2));

        int expirados = service.expirarPendientesAnterioresA(LocalDate.of(2026, 8, 24));

        assertThat(expirados).isEqualTo(2);
        assertThat(r1.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        assertThat(r2.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }

    /**
     * C-6 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): antes, una fila
     * que fallaba al guardar revertia el barrido completo de la noche, dejando a TODOS los
     * registros vencidos (incluso los que hubieran guardado bien) sin expirar. Cada fila
     * ahora se procesa en su propia transaccion: r2 fallando no debe impedir que r1 y r3
     * queden EXPIRADO, y el conteo devuelto debe reflejar solo los que si se guardaron.
     */
    @Test
    @DisplayName("expirarPendientesAnterioresA(): una fila que falla al guardar no tumba el barrido de las demas")
    void expirarPendientesAnterioresAAislaLaFilaQueFalla() {
        RegistroHabito r1 = registroPendiente(participante(), HabitoId.of(UUID.randomUUID()));
        RegistroHabito r2 = registroPendiente(participante(), HabitoId.of(UUID.randomUUID()));
        RegistroHabito r3 = registroPendiente(participante(), HabitoId.of(UUID.randomUUID()));
        when(loadRegistroPort.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE, LocalDate.of(2026, 8, 24)))
                .thenReturn(List.of(r1, r2, r3));
        // saveRegistroPort.save ya tiene un stub lenient generico (setUp); lo sobre-escribimos
        // solo para r2, que simula la fila corrupta del hallazgo.
        when(saveRegistroPort.save(r2)).thenThrow(new IllegalStateException("fila corrupta simulada"));

        int expirados = service.expirarPendientesAnterioresA(LocalDate.of(2026, 8, 24));

        assertThat(expirados).as("solo r1 y r3 se guardaron bien").isEqualTo(2);
        assertThat(r1.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        assertThat(r3.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        // r2 igual queda mutada en memoria (el dominio no sabe que el save fallo), pero eso
        // no importa: nunca se persistio, asi que el proximo barrido la vuelve a intentar.
    }

    // ---- generar: el dia de desbloqueo elegido por el aprendiz ----

    /**
     * El habito elegido "para el dia 2" no puede generar registro el dia 1. Sin este filtro el
     * numero se guardaba en `desbloqueos_habito` y no cambiaba nada: el barrido lo generaba igual.
     */
    @Test
    @DisplayName("generar: un habito elegido para un dia futuro todavia no genera registro")
    void generarSalteaElHabitoElegidoParaMasAdelante() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(1, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(
                DesbloqueoHabito.rehydrate(participante, habito.id(), 2, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).isEmpty();
        verify(saveRegistroPort, never()).insertarSiNoExiste(any());
    }

    /** Contraparte: llegado su dia, el mismo habito si genera. */
    @Test
    @DisplayName("generar: llegado el dia elegido, el habito genera registro normalmente")
    void generarIncluyeElHabitoCuandoLlegaSuDia() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(2, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(
                DesbloqueoHabito.rehydrate(participante, habito.id(), 2, CLOCK.now(), CLOCK.now(), CLOCK.now())));
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(7, 0), null, CLOCK.now())));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).hasSize(1);
        assertThat(generados.get(0).habitoId()).isEqualTo(habito.id());
    }

    /**
     * D-196 (antes: caracterizacion {@code caracterizacionRetrocederApagaUnHabitoActivadoDespuesDelDiaDestino},
     * que fijaba que el habito dejaba de generarse). {@code dia_desbloqueo} es un numero ABSOLUTO y
     * el interruptor del Plan crea la fila con el dia en que se toco (D-99). El aprendiz toco el
     * habito en su dia 30, lo hizo los dias 30 a 32, y un admin lo retrocede al 25: el habito sigue
     * generandose, igual que "Mis habitos" lo sigue mostrando desbloqueado. Contra el codigo viejo
     * {@code generados} sale vacio.
     */
    @Test
    @DisplayName("D-196: tras retroceder al 25, un habito que ya corrio desde el dia 30 se sigue generando")
    void retrocederNoApagaUnHabitoQueYaEstabaActivo() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        conPlanDeUnHabito(participante, habito, 25, 30);
        when(loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(habito.id())))
                .thenReturn(Map.of(habito.id(), 32));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).extracting(RegistroHabito::habitoId).containsExactly(habito.id());
        assertThat(generados.get(0).diaPrograma()).as("el snapshot es el dia de HOY, no se reescribe nada")
                .isEqualTo(25);
    }

    /**
     * La contraparte: lo que se eligio para MAS ADELANTE y todavia no llego sigue esperando su
     * dia, aunque el habito ya se generara ANTES de elegirlo (registros con dia menor al de
     * desbloqueo: se genero sin fila en el plan, y despues se lo postergo).
     */
    @Test
    @DisplayName("D-196: un habito postergado a un dia que no llego no se genera por tener registros viejos")
    void unHabitoPostergadoNoSeActivaPorRegistrosAnterioresASuDia() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        conPlanDeUnHabito(participante, habito, 25, 30);
        when(loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(habito.id())))
                .thenReturn(Map.of(habito.id(), 24));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).isEmpty();
    }

    /** Sin ningun registro del habito, un desbloqueo futuro sigue su regla de siempre. */
    @Test
    @DisplayName("D-196: sin registros, el desbloqueo futuro sigue dejando el habito afuera")
    void sinRegistrosElDesbloqueoFuturoSigueDejandoloAfuera() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        conPlanDeUnHabito(participante, habito, 25, 30);

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // D-200 (decision del dueño 2026-09-27): al retroceder el dia, un habito PERSONAL, o uno
    // con horario que arranca despues del dia destino, que YA CORRIO se sigue generando. Lo que
    // nunca corrio sigue esperando su dia. Mismo criterio que D-196 (arriba).
    // ────────────────────────────────────────────────────────────────────────────────────

    private static Habito habitoPersonalDe(UserId dueno) {
        return Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), dueno, "Correr 5km", TipoHabito.CHECKBOX,
                "CUERPO", com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal.OTRO, "meta",
                CLOCK.now());
    }

    /** Como nace el horario de un habito PERSONAL ({@code MisHabitosService.crear}): abierto y TODOS. */
    private static HorarioHabito horarioDesdeElDia(HabitoId habitoId, int diaInicio, TipoDia tipo) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habitoId, diaInicio, null, tipo,
                LocalTime.of(7, 0), null, CLOCK.now());
    }

    private void conUnHabitoPersonal(UserId participante, Habito habito, int diaDeHoy, HorarioHabito horario) {
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(diaDeHoy, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of());
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(horario));
    }

    /** Como {@link #conUnHabitoPersonal}, pero del catalogo: su {@code dia_inicio} es del programa. */
    private void conUnHabitoDelCatalogo(UserId participante, Habito habito, int diaDeHoy, HorarioHabito horario) {
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(diaDeHoy, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(horario));
    }

    /**
     * Un habito del catalogo que arranca tarde, como la Audioterapia (dia 11). La persona lo hizo del
     * 11 al 12 y un admin la retrocede al 9. Contra el codigo anterior a D-200 {@code generados}
     * salia vacio.
     *
     * <p><b>Corregido 2026-09-27 (D-216).</b> Esta prueba y la siguiente usaban el habito PERSONAL
     * del dia 30. Desde D-216 crear un habito personal ya prueba que la persona llego a su primer
     * dia (se prueba abajo, TZ-15), asi que el criterio de los registros se prueba con el catalogo.
     */
    @Test
    @DisplayName("D-200: tras retroceder al 9, un habito del catalogo que arranca el 11 y ya corrio se sigue generando")
    void retrocederNoApagaUnHabitoDelCatalogoQueYaCorria() {
        UserId participante = participante();
        Habito audioterapia = habitoCheckbox();
        conUnHabitoDelCatalogo(participante, audioterapia, 9, horarioDesdeElDia(audioterapia.id(), 11, TipoDia.TODOS));
        when(loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(audioterapia.id())))
                .thenReturn(Map.of(audioterapia.id(), 12));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).extracting(RegistroHabito::habitoId).containsExactly(audioterapia.id());
        assertThat(generados.get(0).diaPrograma()).as("el snapshot es el dia REAL, no el del horario")
                .isEqualTo(9);
    }

    /** Registros anteriores al inicio del horario no cuentan: ese habito nunca llego a correr desde ahi. */
    @Test
    @DisplayName("D-200: un habito cuyo horario arranca despues y nunca corrio desde ahi sigue esperando su dia")
    void unHabitoQueNuncaCorrioDesdeSuInicioSigueEsperando() {
        UserId participante = participante();
        Habito audioterapia = habitoCheckbox();
        conUnHabitoDelCatalogo(participante, audioterapia, 9, horarioDesdeElDia(audioterapia.id(), 11, TipoDia.TODOS));
        when(loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(audioterapia.id())))
                .thenReturn(Map.of(audioterapia.id(), 10));

        assertThat(service.generar(participante, LocalDate.of(2026, 8, 24))).isEmpty();
    }

    /**
     * TZ-15 (e2e del 2026-09-27, D-216), con el reloj en madrugada UTC (regla 03): el 28/09 a las
     * 03:00 UTC en Lima todavia es el 27 a las 22:00. La persona creo su habito propio el dia 30,
     * cuando los registros de ese dia ya estaban hechos, y la retrocedieron al 25: no tiene ningun
     * registro, pero lo creo estando en el dia 30, asi que venia corriendo. Se genera para el 27 de
     * Lima con el dia REAL como snapshot. Contra el codigo viejo no se generaba hasta volver al 30.
     */
    @Test
    @DisplayName("TZ-15: tras retroceder al 25, un habito propio creado el dia 30 sin registros todavia se sigue generando")
    void unHabitoPropioCreadoAntesDelRetrocesoSeGeneraAunqueNoTengaRegistros() {
        RegistroService deMadrugada = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort,
                loadHorarioPort, loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder,
                loadDesbloqueoPort, events, FixedClock.at(Instant.parse("2026-09-28T03:00:00Z")), idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                transactionManager);
        UserId participante = participante();
        Habito personal = habitoPersonalDe(participante);
        conUnHabitoPersonal(participante, personal, 25, horarioDesdeElDia(personal.id(), 30, TipoDia.TODOS));

        List<RegistroHabito> generados = deMadrugada.generarDiaCompletoEnSuZona(participante);

        assertThat(generados).singleElement().satisfies(registro -> {
            assertThat(registro.habitoId()).isEqualTo(personal.id());
            assertThat(registro.fechaEjecucion()).as("el dia de Lima, no el del servidor")
                    .isEqualTo(LocalDate.of(2026, 9, 27));
            assertThat(registro.diaPrograma()).as("snapshot: el dia real").isEqualTo(25);
        });
    }

    /** El barrido del padron no paga una consulta mas cuando nada quedo por encima del dia de hoy. */
    @Test
    @DisplayName("D-200: sin horarios ni desbloqueos por encima del dia de hoy no se leen registros")
    void sinNadaPorEncimaDeHoyNoSeLeenRegistros() {
        UserId participante = participante();
        Habito personal = habitoPersonalDe(participante);
        conUnHabitoPersonal(participante, personal, 31, horarioDesdeElDia(personal.id(), 30, TipoDia.TODOS));

        assertThat(service.generar(participante, LocalDate.of(2026, 8, 24))).hasSize(1);
        verify(loadRegistroPort, never()).diaProgramaMasAltoGeneradoPorHabito(any(), any());
    }

    /**
     * Regla 03, reloj en madrugada UTC: el lunes 31/08 a las 04:30 UTC en Lima todavia es el
     * domingo 30 (23:30). Un habito de domingo del catalogo (arranca el dia 35) que ya corrio, con
     * la persona retrocedida al 33, se genera para ESE domingo de Lima. Con la fecha del servidor
     * seria lunes y no le tocaria; contra el codigo viejo no se genera nunca.
     */
    @Test
    @DisplayName("D-200: un habito de domingo que ya corrio se genera el domingo de Lima con el reloj en madrugada UTC")
    void unHabitoDeDomingoQueYaCorrioSeGeneraElDomingoDeLima() {
        RegistroService deMadrugada = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort,
                loadHorarioPort, loadPreferenciaPort, progresoPort, ajustarPuntosPort, publicacionMuroFinder,
                loadDesbloqueoPort, events, FixedClock.at(Instant.parse("2026-08-31T04:30:00Z")), idGenerator,
                List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad(), new PoliticaClaseDiaria()),
                transactionManager);
        UserId participante = participante();
        Habito descanso = habitoCheckbox();
        when(progresoPort.deParticipante(participante)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(33, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(descanso));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadHorarioPort.porHabitos(List.of(descanso.id())))
                .thenReturn(List.of(horarioDesdeElDia(descanso.id(), 35, TipoDia.DOMINGO)));
        when(loadRegistroPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(descanso.id())))
                .thenReturn(Map.of(descanso.id(), 35));

        List<RegistroHabito> generados = deMadrugada.generarDiaCompletoEnSuZona(participante);

        assertThat(generados).singleElement().satisfies(registro -> {
            assertThat(registro.fechaEjecucion()).isEqualTo(LocalDate.of(2026, 8, 30));
            assertThat(registro.tipoDia()).isEqualTo(TipoDia.DOMINGO);
            assertThat(registro.diaPrograma()).isEqualTo(33);
        });
    }

    private void conPlanDeUnHabito(UserId participante, Habito habito, int diaDeHoy, int diaDesbloqueo) {
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(diaDeHoy, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(DesbloqueoHabito.rehydrate(
                participante, habito.id(), diaDesbloqueo, CLOCK.now(), CLOCK.now(), CLOCK.now())));
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(7, 0), null, CLOCK.now())));
        lenient().when(saveRegistroPort.insertarSiNoExiste(any())).thenReturn(true);
    }

    /**
     * E-230: dos pedidos a /hoy en el mismo milisegundo ven "no existe" y los dos insertan. El que
     * pierde ya no revienta con la UNIQUE (antes: 409): el INSERT idempotente dice que ya estaba y
     * la generacion sigue sin agregarlo.
     */
    @Test
    @DisplayName("generar: si otro pedido simultaneo ya creo el track, no falla ni lo cuenta dos veces")
    void generarToleraElTrackCreadoPorOtroPedido() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(2, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadHorarioPort.porHabitos(List.of(habito.id()))).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(7, 0), null, CLOCK.now())));
        when(saveRegistroPort.insertarSiNoExiste(any())).thenReturn(false);

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).isEmpty();
        verify(saveRegistroPort, never()).save(any());
    }
}
