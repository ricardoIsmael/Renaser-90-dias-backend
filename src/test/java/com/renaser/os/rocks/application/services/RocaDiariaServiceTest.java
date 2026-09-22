package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.community.api.PublicarEnMuroPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase.SolicitarUrlAdjuntoRocaCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaDiariaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T20:05:00Z"));

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private LoadRocaSemanalPort loadRocaSemanalPort;
    @Mock
    private LoadRocaDiariaPort loadRocaDiariaPort;
    @Mock
    private SaveRocaDiariaPort saveRocaDiariaPort;
    @Mock
    private RegistrarEvidenciaPort registrarEvidenciaPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private PublicarEnMuroPort publicarEnMuroPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;

    private RocaDiariaService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new RocaDiariaService(loadRocaMaestraPort, loadRocaSemanalPort, loadRocaDiariaPort,
                saveRocaDiariaPort, registrarEvidenciaPort, progresoPort, almacenamientoPort, ajustarPuntosPort,
                publicarEnMuroPort, events, CLOCK, idGenerator);
        actorId = UserId.of(UUID.randomUUID());
        lenient().when(saveRocaDiariaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(registrarEvidenciaPort.registrar(any()))
                .thenReturn(new EvidenciaRegistrada(UUID.randomUUID(), EstadoValidacion.PENDIENTE));
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 1, 5), ZoneOffset.UTC, rol, suspendido, false);
    }

    /** Igual que {@link #progreso} pero con el programa ANDANDO: el caso de E-169. */
    private static ProgresoParticipanteRocks progresoActivado(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 1, 5), ZoneOffset.UTC, rol, suspendido, true);
    }

    private RocaDiaria rocaVerde(LocalTime horaFin) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), actorId,
                LocalDate.of(2026, 8, 24), 1, "verde", null, 5, false,
                EjeObjetivo.CUERPO, null, null, horaFin, List.of(), CLOCK);
    }

    private RocaDiaria rocaAmarilla() {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), actorId,
                LocalDate.of(2026, 8, 24), 2, "amarilla", null, 5, false,
                EjeObjetivo.CUERPO, null, null, null, List.of(), CLOCK);
    }

    private CompletarRocaDiariaCommand comandoTexto(RocaDiariaId id) {
        return new CompletarRocaDiariaCommand(actorId, id, TipoEvidenciaRoca.TEXTO, null, null, "hecho", null, null,
                null, true, false);
    }

    /**
     * <b>E-206.</b> Crear el plan del dia no tenia ni un test, y por eso el minimo de 3 sobrevivio a
     * que la semana pasara a poder armarse con un solo eje.
     *
     * <p>La leccion de E-205 decia "un test que construye un comando NO prueba un caso de uso", y
     * aca el comando SI es el lugar correcto: el minimo vive en su {@code @Size} y no hay un segundo
     * minimo en el servicio — {@code RocaDiariaService} solo acota <i>por eje</i> (1 a 3). Se
     * verifico con un grep de la regla, que es lo que fallo la vez anterior.
     */
    /* ------------------------------------------------------------------------------------------
     * QUE FECHAS SE PUEDEN PLANIFICAR
     *
     * El reloj del fixture marca el 2026-08-24 a las 20:05 UTC: la ventana nocturna (18:00) esta
     * ABIERTA, asi que hoy ya no se toca y se planifica de manana en adelante.
     * ---------------------------------------------------------------------------------------- */

    private void conPlanDiarioPosible() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(tresMaestrasParaDiaria());
        lenient().when(loadRocaSemanalPort.deMaestraYSemana(any(), anyInt()))
                .thenAnswer(inv -> Optional.of(RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()),
                        inv.getArgument(0), 2, "objetivo", List.of(), null, null, null, CLOCK)));
        lenient().when(saveRocaDiariaPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
    }

    private List<RocaMaestra> tresMaestrasParaDiaria() {
        Instant ahora = CLOCK.now();
        return List.of(
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.CUERPO,
                        "obj cuerpo", null, ahora, ahora),
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.TRABAJO,
                        "obj trabajo", null, ahora, ahora),
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.RELACIONES,
                        "obj relaciones", null, ahora, ahora));
    }

    private void crearPara(LocalDate fecha) {
        service.crear(new CrearPlanDiarioCommand(actorId, fecha, List.of(itemDiario(EjeObjetivo.CUERPO, 1))));
    }

    @Test
    @DisplayName("manana se puede planificar")
    void mananaSePuede() {
        conPlanDiarioPosible();
        assertThatCode(() -> crearPara(LocalDate.of(2026, 8, 25))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("y tambien el resto de la semana: el ejemplo del dueno era miercoles y jueves")
    void elRestoDeLaSemanaTambien() {
        conPlanDiarioPosible();
        assertThatCode(() -> crearPara(LocalDate.of(2026, 8, 26))).doesNotThrowAnyException();
        assertThatCode(() -> crearPara(LocalDate.of(2026, 8, 27))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("hoy no, con la ventana nocturna abierta: el dia en curso no se reacomoda")
    void hoyNoConLaVentanaAbierta() {
        conPlanDiarioPosible();
        assertThatThrownBy(() -> crearPara(LocalDate.of(2026, 8, 24)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INVALID_DATE");
    }

    @Test
    @DisplayName("la semana que viene tampoco: su objetivo semanal todavia no existe")
    void masAlladeLaSemanaNo() {
        conPlanDiarioPosible();
        assertThatThrownBy(() -> crearPara(LocalDate.of(2026, 9, 7)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("INVALID_DATE");
    }

    @Test
    @DisplayName("un dia futuro ya planificado se REEMPLAZA, no se rechaza: es poder corregirse")
    void unDiaFuturoSeReemplaza() {
        conPlanDiarioPosible();
        LocalDate manana = LocalDate.of(2026, 8, 25);

        crearPara(manana);

        verify(saveRocaDiariaPort).borrarDeParticipanteYFecha(actorId, manana);
    }

    @Test
    @DisplayName("E-206: una sola accion alcanza para planificar el dia")
    void conUnaSolaAccionSeAcepta() {
        assertThatCode(() -> new CrearPlanDiarioCommand(actorId, LocalDate.of(2026, 1, 24),
                List.of(itemDiario(EjeObjetivo.CUERPO, 1)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("...pero ninguna no: un dia sin objetivos no es un dia planificado")
    void sinNingunaSeRechaza() {
        assertThatThrownBy(() -> new CrearPlanDiarioCommand(actorId, LocalDate.of(2026, 1, 24), List.of()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el tope sigue en nueve: tres por eje, tres ejes")
    void masDeNueveSeRechaza() {
        List<ItemRocaDiaria> diez = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> itemDiario(EjeObjetivo.CUERPO, i)).toList();
        assertThatThrownBy(() -> new CrearPlanDiarioCommand(actorId, LocalDate.of(2026, 1, 24), diez))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private static ItemRocaDiaria itemDiario(EjeObjetivo eje, int posicion) {
        return new ItemRocaDiaria(eje, posicion, "Caminar 40 minutos", null, 5, false, null, null, null);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.completar(comandoTexto(RocaDiariaId.of(UUID.randomUUID()))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.completar(comandoTexto(RocaDiariaId.of(UUID.randomUUID()))))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void completarUnaRocaYaCompletadaEsConflicto() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria completada = rocaVerde(null);
        completada.completar(CLOCK.now(), CLOCK);
        when(loadRocaDiariaPort.byIdParaEscritura(completada.id())).thenReturn(Optional.of(completada));

        assertThatThrownBy(() -> service.completar(comandoTexto(completada.id())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ley IV: AMARILLA/ROJA bloqueadas mientras la VERDE del eje no tenga evidencia")
    void amarillaBloqueadaSinVerdeCompletada() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria amarilla = rocaAmarilla();
        RocaDiaria verdeSinCompletar = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(amarilla.id())).thenReturn(Optional.of(amarilla));
        when(loadRocaDiariaPort.deParticipanteYFecha(actorId, amarilla.fecha()))
                .thenReturn(List.of(amarilla, verdeSinCompletar));

        assertThatThrownBy(() -> service.completar(comandoTexto(amarilla.id())))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("GREEN_NOT_EVIDENCED");
    }

    @Test
    @DisplayName("Ley VI: EXIF de una FOTO a mas de 15 min del instante de subida se rechaza")
    void exifFueraDeMargenRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));

        Instant exifMuyViejo = CLOCK.now().minus(Duration.ofMinutes(20));
        var command = new CompletarRocaDiariaCommand(actorId, verde.id(), TipoEvidenciaRoca.FOTO, "bucket", "ruta",
                null, exifMuyViejo, null, null, true, false);

        assertThatThrownBy(() -> service.completar(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXIF_MISMATCH");
    }

    @Test
    @DisplayName("completar paga puntos sincronicamente via AjustarPuntosPort, ROCK_COMPLETED a tiempo")
    void completarATiempoPagaRockCompleted() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(LocalTime.of(21, 0)); // 21:00 UTC, completando a las 20:05 -> a tiempo
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));
        when(ajustarPuntosPort.ajustar(eq(actorId), eq(MotivoPuntos.ROCK_COMPLETED), eq(10), anyString()))
                .thenReturn(new ResumenAjustePuntos(actorId, 10, 110));

        RocaDiaria resultado = service.completar(comandoTexto(verde.id()));

        assertThat(resultado.completada()).isTrue();
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(ajustarPuntosPort).ajustar(eq(actorId), eq(MotivoPuntos.ROCK_COMPLETED), eq(10), anyString());
        // any() sin tipo es ambiguo: ApplicationEventPublisher esta sobrecargado
        // (ApplicationEvent vs Object) y RocaCompletadaEvent no extiende ApplicationEvent,
        // asi que la llamada real resuelve al overload Object — hay que forzar el mismo
        // overload aca o Mockito verifica sobre la sobrecarga equivocada (ver BITACORA_ERRORES).
        verify(events).publishEvent(any(RocaCompletadaEvent.class));
    }

    @Test
    @DisplayName("D-P6/idempotencia: si ya tiene puntos otorgados, no se vuelve a llamar a AjustarPuntosPort")
    void noVuelveAPagarSiYaTienePuntos() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        verde.otorgarPuntos(10); // simula que un intento anterior ya pago (defensivo, no deberia pasar con completada=false)
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));

        service.completar(comandoTexto(verde.id()));

        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Hueco #17: esPrincipal viaja del comando al RegistrarEvidenciaComando, ya no hardcodeado en true")
    void esPrincipalViajaAlComandoDeEvidencia() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));
        var command = new CompletarRocaDiariaCommand(actorId, verde.id(), TipoEvidenciaRoca.TEXTO, null, null,
                "hecho", null, null, null, false, false);

        service.completar(command);

        verify(registrarEvidenciaPort).registrar(argThat(c -> !c.esPrincipal()));
    }

    @Test
    @DisplayName("Hueco #17: publishedToWall solo aplica a evidencia visual (FOTO/VIDEO/CAPTURA)")
    void publishedToWallConEvidenciaNoVisualEsRechazado() {
        assertThatThrownBy(() -> new CompletarRocaDiariaCommand(actorId, RocaDiariaId.of(UUID.randomUUID()),
                TipoEvidenciaRoca.TEXTO, null, null, "hecho", null, null, null, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedToWall");
    }

    @Test
    @DisplayName("Hueco #17: publishedToWall=true llama a community.api.PublicarEnMuroPort en la misma transaccion")
    void publishedToWallLlamaAlPuertoDeCommunity() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));
        /* Corregido 2026-09-18: el fixture decia "rocas/x/y", una ruta que NO pertenece al actor.
           Pasaba porque nadie comprobaba el prefijo — o sea que esta prueba fijaba el
           comportamiento inseguro. Ahora usa la clave que el servidor emite de verdad. */
        String rutaPropia = "rocas/" + actorId.value() + "/" + verde.id().value();
        var command = new CompletarRocaDiariaCommand(actorId, verde.id(), TipoEvidenciaRoca.FOTO, "renaser-files",
                rutaPropia, null, CLOCK.now(), null, null, true, true);

        service.completar(command);

        verify(publicarEnMuroPort).publicarDesdeEvidencia(argThat(
                c -> c.autorId().equals(actorId) && c.bucket().equals("renaser-files") && c.ruta().equals(rutaPropia)
                        && c.mime().equals("image/jpeg") && c.texto().contains("verde")));
    }

    @Test
    @DisplayName("La evidencia de una roca no puede apuntar al archivo de otra persona")
    void evidenciaConRutaAjenaSeRechaza() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));
        /* Con `publishedToWall=true` esto llegaba al Muro y `aVista` firmaba la clave ajena para
           CADA lector del feed: la firma del Pacto de Sangre de otra persona repartida al padron. */
        var command = new CompletarRocaDiariaCommand(actorId, verde.id(), TipoEvidenciaRoca.CAPTURA, "renaser-files",
                "firmas/" + java.util.UUID.randomUUID() + "/fase_1.svg", null, CLOCK.now(), null, null, true, true);

        assertThatThrownBy(() -> service.completar(command))
                .isInstanceOf(IllegalArgumentException.class);
        verify(publicarEnMuroPort, org.mockito.Mockito.never())
                .publicarDesdeEvidencia(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Hueco #17: publishedToWall=false (default) nunca llama a community")
    void publishedToWallFalseNoLlamaANadie() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byIdParaEscritura(verde.id())).thenReturn(Optional.of(verde));

        service.completar(comandoTexto(verde.id()));

        verify(publicarEnMuroPort, never()).publicarDesdeEvidencia(any());
    }

    @Test
    void solicitarUrlDevuelveUrlFirmadaDeAlmacenamientoPort() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaDiaria verde = rocaVerde(null);
        when(loadRocaDiariaPort.byId(verde.id())).thenReturn(Optional.of(verde));
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any()))
                .thenReturn(URI.create("https://s3.example/rocas/x"));

        var url = service.solicitarUrl(new SolicitarUrlAdjuntoRocaCommand(actorId, verde.id(), "image/jpeg"));

        assertThat(url.bucket()).isEqualTo("renaser-files");
        assertThat(url.url().toString()).isEqualTo("https://s3.example/rocas/x");
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
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoActivado(RolParticipante.MENTOR, false)));

        GuardDeRol.noRechaza(() -> service.completar(comandoTexto(RocaDiariaId.of(UUID.randomUUID()))), "Solo un aprendiz opera sus propias rocas")
                ;
    }
}
