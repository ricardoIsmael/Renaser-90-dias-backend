package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase.PreguntarRenasiaCommand;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.ConsultarLeccionesVisiblesPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COMPANION;
import static com.renaser.os.rag.domain.model.conversacion.AgenteConversacional.COURSE_TUTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orquestacion de los dos asistentes (D-102) sobre puertos mockeados. Las reglas de dominio
 * ({@code MensajeRenasia}, {@code EventoRenasia}) tienen sus propios tests; aca se verifica que el
 * caso de uso las combina bien: memoria e historial por agente, contexto acotado al curso para el
 * tutor, ambito solo para el tutor, y las garantias previas de cuota y streaming.
 */
@ExtendWith(MockitoExtension.class)
class ConversacionRenasiaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));
    /** Id fijo que devuelve el IdGenerator mockeado, mismo espiritu que el FixedClock de arriba. */
    private static final UUID ID_GENERADO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ControlCuotaRenasiaPort controlCuotaRenasiaPort;
    @Mock
    private LoadConversacionRenasiaPort loadConversacionRenasiaPort;
    @Mock
    private SaveConversacionRenasiaPort saveConversacionRenasiaPort;
    @Mock
    private LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    @Mock
    private SaveMensajeRenasiaPort saveMensajeRenasiaPort;
    @Mock
    private VectorStorePort vectorStorePort;
    @Mock
    private ConsultarLeccionesVisiblesPort consultarLeccionesVisiblesPort;
    @Mock
    private ChatIAPort chatIAPort;
    @Mock
    private IdGenerator idGenerator;

    private ConversacionRenasiaService service;

    private final UserId activo = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ConversacionRenasiaService(userSummaryFinder, controlCuotaRenasiaPort,
                loadConversacionRenasiaPort, saveConversacionRenasiaPort, loadMensajeRenasiaPort,
                saveMensajeRenasiaPort, vectorStorePort, consultarLeccionesVisiblesPort, chatIAPort, CLOCK,
                idGenerator);
        // lenient: no todos los casos llegan a generar un id (varios cortan antes, en autorizacion o cuota).
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(consultarLeccionesVisiblesPort.visiblesParaActor(any())).thenReturn(Set.of());
        lenient().when(consultarLeccionesVisiblesPort.visiblesParaActorEnCurso(any(), any())).thenReturn(Set.of());
        lenient().when(userSummaryFinder.findById(activo)).thenReturn(
                Optional.of(new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(controlCuotaRenasiaPort.intentarConsumir(any())).thenReturn(true);
        lenient().when(saveMensajeRenasiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveConversacionRenasiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Pregunta al acompanante, sin ambito ni curso: el chat general del programa. */
    private PreguntarRenasiaCommand pregunta(UserId actorId) {
        return new PreguntarRenasiaCommand(actorId, COMPANION, "que es Renasia?", null, null);
    }

    /** Pregunta a Sparkie desde adentro de un curso. */
    private PreguntarRenasiaCommand preguntaAlTutor(UserId actorId, String cursoId) {
        return new PreguntarRenasiaCommand(actorId, COURSE_TUTOR, "que dice la leccion?", "el curso \"X\"", cursoId);
    }

    /** Un stream mínimo y válido para los tests a los que no les importa el contenido de la respuesta. */
    private static Flux<EventoRenasia> streamOk() {
        return Flux.just(new EventoRenasia.Texto("ok"), new EventoRenasia.Fin());
    }

    private void stubCaminoFeliz() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any())).thenReturn(List.of());
        when(chatIAPort.responder(any())).thenReturn(streamOk());
    }

    private Consulta consultaEnviadaAlModelo() {
        ArgumentCaptor<Consulta> captor = ArgumentCaptor.forClass(Consulta.class);
        verify(chatIAPort).responder(captor.capture());
        return captor.getValue();
    }

    @Test
    void preguntarRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.preguntar(pregunta(suspendido))).isInstanceOf(NotAuthorizedException.class);

        verify(saveMensajeRenasiaPort, never()).save(any());
        verify(controlCuotaRenasiaPort, never()).intentarConsumir(any());
    }

    @Test
    void preguntarRechazaCuandoSeAgotoLaCuota() {
        when(controlCuotaRenasiaPort.intentarConsumir(activo)).thenReturn(false);

        assertThatThrownBy(() -> service.preguntar(pregunta(activo))).isInstanceOf(RateLimitExceededException.class);

        verify(saveMensajeRenasiaPort, never()).save(any());
    }

    @Test
    void preguntarLiberaLaCuotaSiFallaLaBusquedaDeContexto() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any()))
                .thenThrow(new RuntimeException("pgvector no disponible"));

        assertThatThrownBy(() -> service.preguntar(pregunta(activo))).isInstanceOf(RuntimeException.class);

        verify(controlCuotaRenasiaPort).liberar(activo);
    }

    /**
     * D-100: el fallo del modelo ya NO revienta el stream. Antes esta prueba esperaba una
     * excepcion; el efecto real de eso era que el controller convertia el error en un `fin`
     * pelado y el aprendiz veia su pregunta sin ninguna respuesta ni motivo. Ahora el caso de
     * uso emite un `error` apto para mostrar y despues el `fin` del contrato SSE. La cuota se
     * sigue liberando (la pregunta no se respondio) y no se persiste ninguna respuesta.
     */
    @Test
    void preguntarLiberaLaCuotaSiElStreamDeIaFalla() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any())).thenReturn(List.of());
        when(chatIAPort.responder(any())).thenReturn(Flux.error(new RuntimeException("Gemini no responde")));

        List<EventoRenasia> eventos = service.preguntar(pregunta(activo)).collectList().block();

        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0)).isInstanceOf(EventoRenasia.Error.class);
        assertThat(((EventoRenasia.Error) eventos.get(0)).mensaje())
                .isEqualTo(ConversacionRenasiaService.MENSAJE_ERROR_MODELO);
        assertThat(eventos.get(1)).isInstanceOf(EventoRenasia.Fin.class);
        verify(controlCuotaRenasiaPort).liberar(activo);
        // Solo se guardo la pregunta del usuario: nunca una respuesta vacia del asistente.
        verify(saveMensajeRenasiaPort, times(1)).save(any());
    }

    /**
     * D-100: memoria de la conversacion. Los ultimos turnos viajan al modelo en orden cronologico
     * (el puerto los devuelve del mas nuevo al mas viejo) y SIN la pregunta actual — se leen antes
     * de guardarla, para no mandarla dos veces.
     */
    @Test
    void preguntarLePasaAlModeloLosTurnosPreviosEnOrdenCronologico() {
        stubCaminoFeliz();
        MensajeRenasia primero = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), activo,
                COMPANION, "hola", CLOCK.now().minusSeconds(20));
        MensajeRenasia segundo = MensajeRenasia.escribirDeAsistente(MensajeRenasiaId.of(UUID.randomUUID()), activo,
                COMPANION, "hola, como estas", List.of(), CLOCK.now().minusSeconds(10));
        // El puerto pagina del mas nuevo al mas viejo.
        when(loadMensajeRenasiaPort.pagina(eq(activo), eq(COMPANION), any(), eq(10)))
                .thenReturn(List.of(segundo, primero));

        service.preguntar(new PreguntarRenasiaCommand(activo, COMPANION, "y que te dije recien?", null, null))
                .collectList().block();

        Consulta consulta = consultaEnviadaAlModelo();
        assertThat(consulta.pregunta()).isEqualTo("y que te dije recien?");
        assertThat(consulta.historial()).containsExactly(primero, segundo);
    }

    /**
     * D-102: la memoria es POR AGENTE. Lo que la persona hablo con el acompanante no le llega al
     * tutor de cursos como turnos previos (ni al reves) — seria exactamente "juntarlos en un mismo",
     * lo que el dueno pidio no hacer.
     */
    @Test
    @DisplayName("D-102: la memoria del tutor se lee solo de los turnos del tutor")
    void preguntarLeeLaMemoriaSoloDelAgenteQueHabla() {
        stubCaminoFeliz();

        service.preguntar(preguntaAlTutor(activo, "curso-1")).collectList().block();

        verify(loadMensajeRenasiaPort).pagina(eq(activo), eq(COURSE_TUTOR), isNull(), eq(10));
        verify(loadMensajeRenasiaPort, never()).pagina(any(), eq(COMPANION), any(), anyInt());
    }

    /** D-102: la pregunta y la respuesta se guardan con el agente que hablo, para poder releerlas por agente. */
    @Test
    void preguntarGuardaPreguntaYRespuestaConElAgenteQueHablo() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any())).thenReturn(List.of());
        when(chatIAPort.responder(any())).thenReturn(streamOk());

        service.preguntar(preguntaAlTutor(activo, "curso-1")).collectList().block();

        ArgumentCaptor<MensajeRenasia> captor = ArgumentCaptor.forClass(MensajeRenasia.class);
        verify(saveMensajeRenasiaPort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(MensajeRenasia::agente).containsOnly(COURSE_TUTOR);
        assertThat(captor.getAllValues()).extracting(MensajeRenasia::rol)
                .containsExactly(RolMensaje.USUARIO, RolMensaje.ASISTENTE);
    }

    /**
     * D-102: Sparkie responde sobre UN curso. Si el cliente dice cual, el contexto se acota a las
     * lecciones visibles DE ESE curso — no a todo lo visible del catalogo.
     */
    @Test
    @DisplayName("D-102: el tutor con curso acota el contexto a ese curso")
    void preguntarDelTutorAcotaElContextoAlCursoEnQueEsta() {
        stubCaminoFeliz();
        when(consultarLeccionesVisiblesPort.visiblesParaActorEnCurso(activo, "curso-1"))
                .thenReturn(Set.of("leccion-del-curso"));

        service.preguntar(preguntaAlTutor(activo, "curso-1")).collectList().block();

        verify(vectorStorePort).buscarSimilares(eq("que dice la leccion?"), eq(5),
                eq(FiltroLecciones.soloVisibles(Set.of("leccion-del-curso"))));
        verify(consultarLeccionesVisiblesPort, never()).visiblesParaActor(any());
        Consulta consulta = consultaEnviadaAlModelo();
        assertThat(consulta.agente()).isEqualTo(COURSE_TUTOR);
        assertThat(consulta.ambito()).isEqualTo("el curso \"X\"");
    }

    /** Un tutor sin curso (cliente que no lo mando) usa todo lo visible: mejor que quedarse sin material. */
    @Test
    void preguntarDelTutorSinCursoUsaTodoLoVisible() {
        stubCaminoFeliz();
        when(consultarLeccionesVisiblesPort.visiblesParaActor(activo)).thenReturn(Set.of("cualquier-visible"));

        service.preguntar(preguntaAlTutor(activo, null)).collectList().block();

        verify(vectorStorePort).buscarSimilares(anyString(), eq(5),
                eq(FiltroLecciones.soloVisibles(Set.of("cualquier-visible"))));
        verify(consultarLeccionesVisiblesPort, never()).visiblesParaActorEnCurso(any(), any());
    }

    /**
     * D-102: el acompanante no tiene ambito ni curso. Un cliente anterior a D-102 que mande
     * `scope` sin `agent` cae aca y no arrastra nada al prompt (que ya no tiene esa seccion).
     */
    @Test
    @DisplayName("D-102: el acompanante ignora ambito y curso aunque el cliente los mande")
    void preguntarDelAcompananteIgnoraAmbitoYCurso() {
        stubCaminoFeliz();

        service.preguntar(new PreguntarRenasiaCommand(activo, COMPANION, "hola", "el curso \"X\"", "curso-1"))
                .collectList().block();

        Consulta consulta = consultaEnviadaAlModelo();
        assertThat(consulta.agente()).isEqualTo(COMPANION);
        assertThat(consulta.ambito()).isNull();
        verify(consultarLeccionesVisiblesPort).visiblesParaActor(activo);
        verify(consultarLeccionesVisiblesPort, never()).visiblesParaActorEnCurso(any(), any());
    }

    @Test
    void preguntarNoLiberaLaCuotaCuandoElStreamTerminaBien() {
        stubCaminoFeliz();

        service.preguntar(pregunta(activo)).collectList().block();

        verify(controlCuotaRenasiaPort, never()).liberar(any());
    }

    @Test
    void preguntarCreaLaConversacionCuandoElActorNuncaHablóConRenasia() {
        stubCaminoFeliz();

        service.preguntar(pregunta(activo)).collectList().block();

        verify(saveConversacionRenasiaPort).save(any(ConversacionRenasia.class));
    }

    @Test
    void preguntarReusaLaConversacionExistenteSinCrearOtra() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo))
                .thenReturn(Optional.of(ConversacionRenasia.iniciar(activo, CLOCK.now())));
        when(chatIAPort.responder(any())).thenReturn(streamOk());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any())).thenReturn(List.of());

        service.preguntar(pregunta(activo)).collectList().block();

        verify(saveConversacionRenasiaPort, never()).save(any());
    }

    @Test
    void preguntarGuardaLaPreguntaDelUsuarioAntesDeConsumirElStream() {
        stubCaminoFeliz();

        // El armado del Flux (sin suscribirse todavia) ya debe haber guardado la pregunta:
        // es una llamada sincrona dentro del cuerpo de preguntar(), no parte del stream.
        service.preguntar(pregunta(activo));

        verify(saveMensajeRenasiaPort).save(
                org.mockito.ArgumentMatchers.argThat(m -> m.rol() == RolMensaje.USUARIO && m.contenido()
                        .equals("que es Renasia?")));
    }

    @Test
    void preguntarNoPersisteLaRespuestaDelAsistenteHastaQueElStreamTermina() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(chatIAPort.responder(any())).thenReturn(Flux.just(
                new EventoRenasia.Texto("Hola"), new EventoRenasia.Texto(" mundo"), new EventoRenasia.Fin()));
        when(vectorStorePort.buscarSimilares(anyString(), eq(5), any())).thenReturn(List.of(
                new FragmentoRelevante("contexto de la leccion 1", "leccion-1", 0.12),
                new FragmentoRelevante("fragmento sin leccion asociada", null, 0.30)));

        Flux<EventoRenasia> resultado = service.preguntar(pregunta(activo));

        // Todavia no se suscribio nadie al Flux: la respuesta del asistente NO esta guardada.
        verify(saveMensajeRenasiaPort, never()).save(
                org.mockito.ArgumentMatchers.argThat(m -> m.rol() == RolMensaje.ASISTENTE));

        List<EventoRenasia> eventos = resultado.collectList().block();

        // La fuente se inyecta antes del Fin que emitio el puerto (contrato SSE: a lo sumo una vez).
        assertThat(eventos).containsExactly(
                new EventoRenasia.Texto("Hola"),
                new EventoRenasia.Texto(" mundo"),
                new EventoRenasia.Fuentes(List.of("leccion-1")),
                new EventoRenasia.Fin());
        ArgumentCaptor<MensajeRenasia> captor = ArgumentCaptor.forClass(MensajeRenasia.class);
        verify(saveMensajeRenasiaPort, times(2)).save(captor.capture());
        MensajeRenasia respuestaAsistente = captor.getAllValues().stream()
                .filter(m -> m.rol() == RolMensaje.ASISTENTE).findFirst().orElseThrow();
        assertThat(respuestaAsistente.contenido()).isEqualTo("Hola mundo");
        assertThat(respuestaAsistente.fuentes()).extracting("leccionId").containsExactly("leccion-1");
    }

    @Test
    void preguntarFiltraElContextoPorLasLeccionesVisiblesDelActor() {
        stubCaminoFeliz();
        when(consultarLeccionesVisiblesPort.visiblesParaActor(activo)).thenReturn(Set.of("leccion-visible"));

        service.preguntar(pregunta(activo)).collectList().block();

        // El conjunto que academy resolvio para ESTE actor es el que llega, envuelto, a
        // VectorStorePort — no uno vacio ni "sin filtro": el bug que esto cierra es
        // exactamente que la busqueda no respetaba el gate de programa de academy.
        verify(vectorStorePort).buscarSimilares(eq("que es Renasia?"), eq(5),
                eq(FiltroLecciones.soloVisibles(Set.of("leccion-visible"))));
    }

    @Test
    void obtenerHistorialRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.obtenerHistorial(suspendido, COMPANION, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void obtenerHistorialExigeAgente() {
        assertThatThrownBy(() -> service.obtenerHistorial(activo, null, null, 30))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void obtenerHistorialIndicaHayMasCuandoLaPaginaExcedeElLimite() {
        MensajeRenasia m1 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), activo,
                COMPANION, "1", CLOCK.now());
        MensajeRenasia m2 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), activo,
                COMPANION, "2", CLOCK.now());
        when(loadMensajeRenasiaPort.pagina(activo, COMPANION, null, 2)).thenReturn(List.of(m1, m2));

        var pagina = service.obtenerHistorial(activo, COMPANION, null, 1);

        assertThat(pagina.mensajes()).hasSize(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.siguienteCursor()).isEqualTo(m1.creadoEn());
    }

    /** D-102: el historial que ve la persona en el panel de Sparkie es SOLO el de Sparkie. */
    @Test
    void obtenerHistorialPideSoloElHistorialDelAgente() {
        when(loadMensajeRenasiaPort.pagina(activo, COURSE_TUTOR, null, 31)).thenReturn(List.of());

        service.obtenerHistorial(activo, COURSE_TUTOR, null, 30);

        verify(loadMensajeRenasiaPort).pagina(activo, COURSE_TUTOR, null, 31);
        verify(loadMensajeRenasiaPort, never()).pagina(any(), eq(COMPANION), any(), anyInt());
    }
}
