package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase.PreguntarRenasiaCommand;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NOTA: {@code VectorStorePort} y {@code ChatIAPort} los define el agente del agregado
 * `conocimiento` (docs/MODULO_RAG.md §4) — este test programa contra las firmas acordadas
 * en el encargo. Si esos puertos terminan con una firma distinta, este archivo se ajusta en
 * la integracion.
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
                saveMensajeRenasiaPort, vectorStorePort, chatIAPort, CLOCK, idGenerator);
        // lenient: no todos los casos llegan a generar un id (varios cortan antes, en autorizacion o cuota).
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(activo)).thenReturn(
                Optional.of(new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(controlCuotaRenasiaPort.intentarConsumir(any())).thenReturn(true);
        lenient().when(saveMensajeRenasiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveConversacionRenasiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PreguntarRenasiaCommand pregunta(UserId actorId) {
        return new PreguntarRenasiaCommand(actorId, "que es Renasia?");
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
        when(vectorStorePort.buscarSimilares(anyString(), eq(5)))
                .thenThrow(new RuntimeException("pgvector no disponible"));

        assertThatThrownBy(() -> service.preguntar(pregunta(activo))).isInstanceOf(RuntimeException.class);

        verify(controlCuotaRenasiaPort).liberar(activo);
    }

    @Test
    void preguntarLiberaLaCuotaSiElStreamDeIaFalla() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of());
        when(chatIAPort.responder(anyString(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Gemini no responde")));

        assertThatThrownBy(() -> service.preguntar(pregunta(activo)).collectList().block())
                .isInstanceOf(RuntimeException.class);

        verify(controlCuotaRenasiaPort).liberar(activo);
    }

    @Test
    void preguntarNoLiberaLaCuotaCuandoElStreamTerminaBien() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of());
        when(chatIAPort.responder(anyString(), anyList())).thenReturn(Flux.just("ok"));

        service.preguntar(pregunta(activo)).collectList().block();

        verify(controlCuotaRenasiaPort, never()).liberar(any());
    }

    @Test
    void preguntarCreaLaConversacionCuandoElActorNuncaHablóConRenasia() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(chatIAPort.responder(anyString(), anyList())).thenReturn(Flux.just("ok"));
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of());

        service.preguntar(pregunta(activo)).collectList().block();

        verify(saveConversacionRenasiaPort).save(any(ConversacionRenasia.class));
    }

    @Test
    void preguntarReusaLaConversacionExistenteSinCrearOtra() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo))
                .thenReturn(Optional.of(ConversacionRenasia.iniciar(activo, CLOCK.now())));
        when(chatIAPort.responder(anyString(), anyList())).thenReturn(Flux.just("ok"));
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of());

        service.preguntar(pregunta(activo)).collectList().block();

        verify(saveConversacionRenasiaPort, never()).save(any());
    }

    @Test
    void preguntarGuardaLaPreguntaDelUsuarioAntesDeConsumirElStream() {
        when(loadConversacionRenasiaPort.porUsuarioId(activo)).thenReturn(Optional.empty());
        when(chatIAPort.responder(anyString(), anyList())).thenReturn(Flux.just("ok"));
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of());

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
        when(chatIAPort.responder(anyString(), anyList())).thenReturn(Flux.just("Hola", " mundo"));
        when(vectorStorePort.buscarSimilares(anyString(), eq(5))).thenReturn(List.of(
                new FragmentoRelevante("contexto de la leccion 1", "leccion-1", 0.12),
                new FragmentoRelevante("fragmento sin leccion asociada", null, 0.30)));

        Flux<String> resultado = service.preguntar(pregunta(activo));

        // Todavia no se suscribio nadie al Flux: la respuesta del asistente NO esta guardada.
        verify(saveMensajeRenasiaPort, never()).save(
                org.mockito.ArgumentMatchers.argThat(m -> m.rol() == RolMensaje.ASISTENTE));

        List<String> chunks = resultado.collectList().block();

        assertThat(chunks).containsExactly("Hola", " mundo");
        ArgumentCaptor<MensajeRenasia> captor = ArgumentCaptor.forClass(MensajeRenasia.class);
        verify(saveMensajeRenasiaPort, times(2)).save(captor.capture());
        MensajeRenasia respuestaAsistente = captor.getAllValues().stream()
                .filter(m -> m.rol() == RolMensaje.ASISTENTE).findFirst().orElseThrow();
        assertThat(respuestaAsistente.contenido()).isEqualTo("Hola mundo");
        assertThat(respuestaAsistente.fuentes()).extracting("leccionId").containsExactly("leccion-1");
    }

    @Test
    void obtenerHistorialRechazaAUnActorSuspendido() {
        assertThatThrownBy(() -> service.obtenerHistorial(suspendido, null, 30))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void obtenerHistorialIndicaHayMasCuandoLaPaginaExcedeElLimite() {
        MensajeRenasia m1 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), activo, "1",
                CLOCK.now());
        MensajeRenasia m2 = MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), activo, "2",
                CLOCK.now());
        when(loadMensajeRenasiaPort.pagina(activo, null, 2)).thenReturn(List.of(m1, m2));

        var pagina = service.obtenerHistorial(activo, null, 1);

        assertThat(pagina.mensajes()).hasSize(1);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.siguienteCursor()).isEqualTo(m1.creadoEn());
    }
}
