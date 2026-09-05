package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.politica.PoliticaSantuario;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
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
                CLOCK, idGenerator, List.of(new PoliticaSantuario(), new PoliticaPostDiarioComunidad()),
                transactionManager);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
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

    private RegistroHabito registroPendiente(UserId participanteId, HabitoId habitoId) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId, habitoId,
                LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
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
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
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
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));
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
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));
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
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));
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
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false)));

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
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

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
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
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
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

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
                Optional.of(new ProgresoParticipanteHabits(1, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(
                DesbloqueoHabito.rehydrate(participante, habito.id(), 2, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).isEmpty();
        verify(saveRegistroPort, never()).save(any());
    }

    /** Contraparte: llegado su dia, el mismo habito si genera. */
    @Test
    @DisplayName("generar: llegado el dia elegido, el habito genera registro normalmente")
    void generarIncluyeElHabitoCuandoLlegaSuDia() {
        UserId participante = participante();
        Habito habito = habitoCheckbox();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(2, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(habito));
        when(loadHabitoPort.personalesActivosDe(participante)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(participante)).thenReturn(List.of(
                DesbloqueoHabito.rehydrate(participante, habito.id(), 2, CLOCK.now(), CLOCK.now(), CLOCK.now())));
        when(loadRegistroPort.porParticipanteHabitoYFecha(eq(participante), eq(habito.id()), any()))
                .thenReturn(Optional.empty());
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(
                HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 1, null, TipoDia.TODOS,
                        LocalTime.of(7, 0), null, CLOCK.now())));

        List<RegistroHabito> generados = service.generar(participante, LocalDate.of(2026, 8, 24));

        assertThat(generados).hasSize(1);
        assertThat(generados.get(0).habitoId()).isEqualTo(habito.id());
    }
}
