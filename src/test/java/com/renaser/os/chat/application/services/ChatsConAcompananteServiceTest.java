package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AcompanamientoDelGrupoPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El chat de dos con quien acompaña (D-173): uno por aprendiz y acompañante, sin repetir los que
 * ya existen, sin staff, y sin que una pareja que falla deje sin chat a las demás.
 *
 * <p>Sin Spring: {@code PlatformTransactionManager} sin stubbing hace que {@code TransactionTemplate}
 * ejecute el callback directo, mismo criterio que {@code ConversacionSoporteServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ChatsConAcompananteServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-26T03:00:00Z"));
    private static final UUID GRUPO = UUID.fromString("99999999-9999-4999-8999-999999999999");

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final UserId LUIS = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final UserId MENTORA = UserId.of(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"));
    private static final UserId GUIA = UserId.of(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"));

    @Mock
    private AcompanamientoDelGrupoPort acompanamientoPort;
    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private SaveConversacionPort saveConversacionPort;
    @Mock
    private AgregarParticipantePort agregarParticipantePort;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final Set<UserId> suspendidos = new HashSet<>();
    /** Todos existen y están activos salvo los de {@link #suspendidos}. */
    private final UserSummaryFinder cuentas = new UserSummaryFinder() {
        @Override
        public java.util.Optional<UserSummary> findById(UserId id) {
            return java.util.Optional.of(cuenta(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
            Map<UserId, UserSummary> encontradas = new LinkedHashMap<>();
            ids.forEach(id -> encontradas.put(id, cuenta(id)));
            return encontradas;
        }

        @Override
        public List<UserSummary> aprendicesActivos() {
            return List.of();
        }

        @Override
        public java.util.Optional<UserSummary> findByEmail(String email) {
            return java.util.Optional.empty();
        }

        private UserSummary cuenta(UserId id) {
            return new UserSummary(id, "Persona", null, UserRole.TRAINEE,
                    suspendidos.contains(id) ? UserStatus.SUSPENDED : UserStatus.ACTIVE);
        }
    };

    private ChatsConAcompananteService service;

    @BeforeEach
    void preparar() {
        service = new ChatsConAcompananteService(acompanamientoPort, loadConversacionPort, saveConversacionPort,
                agregarParticipantePort, CLOCK, idGenerator, cuentas, transactionManager);
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(saveConversacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(loadConversacionPort.clavesDirectasExistentes(anyCollection())).thenReturn(Set.of());
    }

    @Test
    @DisplayName("asignar la mentora abre un chat de dos con cada aprendiz, con solo esos dos adentro")
    void abreUnChatDeDosPorAprendiz() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA, LUIS));

        int abiertas = service.abrirParaGrupo(GRUPO);

        assertThat(abiertas).isEqualTo(2);
        ArgumentCaptor<Conversacion> guardadas = ArgumentCaptor.forClass(Conversacion.class);
        verify(saveConversacionPort, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(Conversacion::tipo).containsOnly(TipoConversacion.DIRECTA);
        assertThat(guardadas.getAllValues()).extracting(Conversacion::claveDirecta).containsExactlyInAnyOrder(
                Conversacion.claveDirectaDe(ANA, MENTORA), Conversacion.claveDirectaDe(LUIS, MENTORA));
        assertThat(participantesAgregados()).containsExactlyInAnyOrder(ANA, MENTORA, LUIS, MENTORA);
    }

    @Test
    @DisplayName("en la recepción, cada aprendiz queda con un chat por cada guía")
    void enLaRecepcionUnChatPorGuia() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA, GUIA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA));

        assertThat(service.abrirParaGrupo(GRUPO)).isEqualTo(2);
    }

    @Test
    @DisplayName("los chats que ya existen no se vuelven a crear: se pregunta una sola vez por todas las parejas")
    void noRepiteLosQueYaExisten() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA, LUIS));
        when(loadConversacionPort.clavesDirectasExistentes(anyCollection()))
                .thenReturn(Set.of(Conversacion.claveDirectaDe(ANA, MENTORA)));

        assertThat(service.abrirParaGrupo(GRUPO)).isEqualTo(1);

        verify(loadConversacionPort, times(1)).clavesDirectasExistentes(anyCollection());
        verify(loadConversacionPort, never()).porClaveDirecta(any());
    }

    @Test
    @DisplayName("un grupo sin mentor ni guías no abre nada ni consulta la base")
    void sinAcompananteNoAbreNada() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of());

        assertThat(service.abrirParaGrupo(GRUPO)).isZero();

        verify(acompanamientoPort, never()).aprendicesVigentes(any());
        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    @DisplayName("quien es aprendiz y acompañante a la vez no recibe un chat consigo mismo")
    void nadieHablaSolo() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(MENTORA, ANA));

        assertThat(service.abrirParaGrupo(GRUPO)).isEqualTo(1);
    }

    @Test
    @DisplayName("G-3: un fallo que no es la carrera no se traga: se abren las demás y después se lanza para que el outbox reintente")
    void unFalloDeVerdadSePropagaDespuesDeIntentarLasDemas() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA, LUIS));
        when(saveConversacionPort.save(any()))
                .thenThrow(new IllegalStateException("se cayó la conexión"))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.abrirParaGrupo(GRUPO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudieron abrir 1 chats")
                .hasRootCauseMessage("se cayó la conexión");
        verify(saveConversacionPort, times(2)).save(any());
    }

    @Test
    @DisplayName("G-4: no se abre un chat de dos con un aprendiz SUSPENDIDO ni con un acompañante SUSPENDIDO")
    void sinChatConSuspendidos() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA, GUIA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA, LUIS));
        suspendidos.add(LUIS);
        suspendidos.add(GUIA);

        assertThat(service.abrirParaGrupo(GRUPO)).isEqualTo(1);

        ArgumentCaptor<Conversacion> guardadas = ArgumentCaptor.forClass(Conversacion.class);
        verify(saveConversacionPort).save(guardadas.capture());
        assertThat(guardadas.getValue().claveDirecta()).isEqualTo(Conversacion.claveDirectaDe(ANA, MENTORA));
    }

    @Test
    @DisplayName("si otro camino lo abrió primero, las demás igual reciben su chat y no es un error")
    void unaParejaQueFallaNoFrenaALasDemas() {
        when(acompanamientoPort.acompanantesVigentes(GRUPO)).thenReturn(List.of(MENTORA));
        when(acompanamientoPort.aprendicesVigentes(GRUPO)).thenReturn(List.of(ANA, LUIS));
        when(saveConversacionPort.save(any()))
                .thenThrow(new DataIntegrityViolationException("clave_directa duplicada"))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.abrirParaGrupo(GRUPO)).isEqualTo(1);
        verify(saveConversacionPort, times(2)).save(any());
    }

    private List<UserId> participantesAgregados() {
        ArgumentCaptor<Participante> agregados = ArgumentCaptor.forClass(Participante.class);
        verify(agregarParticipantePort, times(4)).agregar(agregados.capture());
        return agregados.getAllValues().stream().map(Participante::usuarioId).toList();
    }
}
