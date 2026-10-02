package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase.PaginaDeSoportes;
import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase.PedidoDeSoportes;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort.ConteoDeSoportes;
import com.renaser.os.chat.application.ports.out.conversacion.SoportesPorActividadPort.SoporteConActividad;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-249: la página de soportes de quien atiende, sin base de datos. */
class SoportesPaginadosServiceTest {

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final BusquedaDeUsuariosFinder busqueda = mock(BusquedaDeUsuariosFinder.class);
    private final SoportesPorActividadPort soportes = mock(SoportesPorActividadPort.class);
    private final LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
    private final LoadMensajePort mensajes = mock(LoadMensajePort.class);
    private final ContarNoLeidosPort noLeidos = mock(ContarNoLeidosPort.class);
    private final NombresDeLosChatsService nombres = mock(NombresDeLosChatsService.class);
    private final SoportesPaginadosService servicio = new SoportesPaginadosService(usuarios, busqueda, soportes,
            conversaciones, mensajes, noLeidos, nombres);

    @BeforeEach
    void actorAdmin() {
        conRol(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        when(mensajes.ultimosPorConversacion(anyList())).thenReturn(Map.of());
        when(noLeidos.contarNoLeidos(any(), anyList())).thenReturn(Map.of());
        when(nombres.nombresDe(anyCollection())).thenReturn(Map.of());
        when(soportes.contar(any(), any())).thenReturn(new ConteoDeSoportes(30, 4));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("D-249: aprendiz, mentor y líder de mentores reciben 403")
    void otrosRolesNo(UserRole rol) {
        UserId quien = UserId.of(UUID.randomUUID());
        conRol(quien, rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.listar(new PedidoDeSoportes(quien, null, null, 25)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(soportes, never()).pagina(any());
    }

    @Test
    @DisplayName("D-249: un ADMIN suspendido recibe 403")
    void suspendidoNo() {
        conRol(admin, UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.listar(new PedidoDeSoportes(admin, null, null, 25)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("D-249: pide una fila de más para saber si hay otra página, y el cursor es la última mostrada")
    void unaDeMas() {
        List<SoporteConActividad> filas = filas(4);
        when(soportes.pagina(any())).thenReturn(filas);
        conversacionesDe(filas);

        PaginaDeSoportes pagina = servicio.listar(new PedidoDeSoportes(admin, null, null, 3));

        ArgumentCaptor<SoportesPorActividadPort.PedidoDeSoportes> pedido =
                ArgumentCaptor.forClass(SoportesPorActividadPort.PedidoDeSoportes.class);
        verify(soportes).pagina(pedido.capture());
        assertThat(pedido.getValue().cuantos()).isEqualTo(4);
        assertThat(pedido.getValue().soloClaves()).isNull();
        assertThat(pagina.conversaciones()).extracting(r -> r.conversacion().id())
                .containsExactly(filas.get(0).id(), filas.get(1).id(), filas.get(2).id());
        assertThat(pagina.siguiente()).isEqualTo(new CursorDeSoportes(filas.get(2).actividad(), filas.get(2).id()));
        assertThat(pagina.total()).isEqualTo(30);
        assertThat(pagina.conNoLeidos()).isEqualTo(4);
    }

    @Test
    @DisplayName("D-249: la última página no trae cursor, y las que siguen a la primera no cuentan de nuevo")
    void ultimaPagina() {
        List<SoporteConActividad> filas = filas(2);
        when(soportes.pagina(any())).thenReturn(filas);
        conversacionesDe(filas);
        CursorDeSoportes desde = new CursorDeSoportes(Instant.now(), ConversacionId.of(UUID.randomUUID()));

        PaginaDeSoportes pagina = servicio.listar(new PedidoDeSoportes(admin, null, desde, 3));

        assertThat(pagina.conversaciones()).hasSize(2);
        assertThat(pagina.siguiente()).isNull();
        assertThat(pagina.total()).isNull();
        verify(soportes, never()).contar(any(), any());
    }

    @Test
    @DisplayName("D-249: la búsqueda filtra por la clave del soporte de quienes coinciden")
    void buscaPorLasClaves() {
        UserId jose = UserId.of(UUID.randomUUID());
        when(busqueda.coincidenCon("josé")).thenReturn(Set.of(jose));
        when(soportes.pagina(any())).thenReturn(List.of());

        servicio.listar(new PedidoDeSoportes(admin, "josé", null, 25));

        ArgumentCaptor<SoportesPorActividadPort.PedidoDeSoportes> pedido =
                ArgumentCaptor.forClass(SoportesPorActividadPort.PedidoDeSoportes.class);
        verify(soportes).pagina(pedido.capture());
        assertThat(pedido.getValue().soloClaves()).containsExactly(Conversacion.claveSoporteDe(jose));
        verify(soportes).contar(eq(admin), eq(Set.of(Conversacion.claveSoporteDe(jose))));
    }

    @Test
    @DisplayName("D-249: si nadie coincide, página vacía con total 0 y sin consultar los soportes")
    void nadieCoincide() {
        when(busqueda.coincidenCon("zzz")).thenReturn(Set.of());

        PaginaDeSoportes pagina = servicio.listar(new PedidoDeSoportes(admin, "zzz", null, 25));

        assertThat(pagina.conversaciones()).isEmpty();
        assertThat(pagina.siguiente()).isNull();
        assertThat(pagina.total()).isZero();
        verify(soportes, never()).pagina(any());
    }

    @Test
    @DisplayName("D-249: el tamaño se acota a 1..50")
    void tamanoAcotado() {
        when(soportes.pagina(any())).thenReturn(List.of());

        servicio.listar(new PedidoDeSoportes(admin, null, null, 5000));

        ArgumentCaptor<SoportesPorActividadPort.PedidoDeSoportes> pedido =
                ArgumentCaptor.forClass(SoportesPorActividadPort.PedidoDeSoportes.class);
        verify(soportes).pagina(pedido.capture());
        assertThat(pedido.getValue().cuantos()).isEqualTo(51);
    }

    private void conRol(UserId id, UserRole rol, UserStatus estado) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Quien", null, rol, estado)));
    }

    private static List<SoporteConActividad> filas(int cuantas) {
        List<SoporteConActividad> filas = new ArrayList<>();
        Instant ahora = Instant.parse("2026-10-02T15:00:00Z");
        for (int i = 0; i < cuantas; i++) {
            filas.add(new SoporteConActividad(ConversacionId.of(UUID.randomUUID()), ahora.minusSeconds(60L * i)));
        }
        return filas;
    }

    /** El puerto las devuelve en otro orden a propósito: el servicio respeta el de la página. */
    private void conversacionesDe(List<SoporteConActividad> filas) {
        List<Conversacion> desordenadas = new ArrayList<>(filas.stream()
                .map(f -> Conversacion.crearSoporte(f.id(), UserId.of(UUID.randomUUID()), "Soporte", f.actividad()))
                .toList());
        java.util.Collections.reverse(desordenadas);
        when(conversaciones.porIds(anyCollection())).thenReturn(desordenadas);
    }
}
