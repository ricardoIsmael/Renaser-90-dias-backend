package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.BorrarRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.CrearRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.EditarRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.ReemplazarRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.RolPersonalizadoEnConsola;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CambioDeRolPersonalizado;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CrearRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.EditarRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.ReemplazoEnBloque;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La consola de roles personalizados (D-269): el orden de los cerrojos y la auditoría. Sin Spring ni base. */
class ConsolaDeRolesPersonalizadosServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T15:00:00Z"));
    private static final String SESION = "sesion-1";

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final GestionarRolesPersonalizadosUseCase gestion = mock(GestionarRolesPersonalizadosUseCase.class);
    private final RolesPersonalizadosFinder finder = mock(RolesPersonalizadosFinder.class);
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private ConsolaDeRolesPersonalizadosService service;
    private AccesoDeConsola acceso;
    private final UUID rolId = UUID.randomUUID();

    @BeforeEach
    void armar() {
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        when(usuarios.findById(admin)).thenReturn(Optional.of(
                new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(usuarios.findByIds(anyCollection())).thenReturn(Map.of(soporte,
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        GuardiaDeSoporteTi guardia = new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15));
        service = new ConsolaDeRolesPersonalizadosService(guardia, gestion, finder, usuarios, auditoria, tokens,
                UUID::randomUUID, CLOCK);
        acceso = new AccesoDeConsola(soporte, tokens.emitirDeConsola(soporte, SESION, Duration.ofMinutes(15)),
                SESION);
    }

    private RolPersonalizadoResumen rol(boolean activo, long personas, Set<String> claves) {
        return new RolPersonalizadoResumen(rolId, "rp:" + rolId, "Mentor junior", null, UserRole.MENTOR, activo,
                claves, personas, 1, soporte, CLOCK.now());
    }

    @Test
    @DisplayName("ADMIN: 403; Soporte TI sin el token de la consola: 423; en los dos, nadie toca users")
    void cerrojos() {
        CrearRolEnConsolaCommand delAdmin = new CrearRolEnConsolaCommand(new AccesoDeConsola(admin, "x", SESION),
                "Mentor junior", null, "MENTOR", List.of(), "m", null);
        CrearRolEnConsolaCommand sinToken = new CrearRolEnConsolaCommand(new AccesoDeConsola(soporte, null, SESION),
                "Mentor junior", null, "MENTOR", List.of(), "m", null);

        assertThatThrownBy(() -> service.crear(delAdmin)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.crear(sinToken)).isInstanceOf(ConsolaBloqueadaException.class);
        assertThatThrownBy(() -> service.listar(new AccesoDeConsola(soporte, "inventado", SESION)))
                .isInstanceOf(ConsolaBloqueadaException.class);
        verifyNoInteractions(gestion, finder);
    }

    @Test
    @DisplayName("crear: valida contra el catálogo (400 fuera de la base o sin motivo) y audita ROL_PERSONALIZADO")
    void crear() {
        assertThatThrownBy(() -> service.crear(new CrearRolEnConsolaCommand(acceso, "Mentor junior", null, "MENTOR",
                List.of("eventos.gestion"), "m", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear(new CrearRolEnConsolaCommand(acceso, "Mentor junior", null,
                "IT_SUPPORT", List.of(), "m", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear(new CrearRolEnConsolaCommand(acceso, "Mentor junior", null, "MENTOR",
                List.of(), " ", null))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gestion);

        when(gestion.crear(any())).thenReturn(new CambioDeRolPersonalizado(null, rol(true, 0, Set.of("muro", "chat"))));
        RolPersonalizadoEnConsola creado = service.crear(new CrearRolEnConsolaCommand(acceso, "Mentor junior", null,
                "MENTOR", List.of("muro", "chat"), "Pedido del dueño", "App/1.6"));

        ArgumentCaptor<CrearRolPersonalizadoCommand> enviado = ArgumentCaptor.forClass(CrearRolPersonalizadoCommand.class);
        verify(gestion).crear(enviado.capture());
        assertThat(enviado.getValue().funcionalidades()).containsExactlyInAnyOrder("muro", "chat");
        assertThat(enviado.getValue().actor()).isEqualTo(soporte);
        assertThat(creado.funcionalidades()).containsExactly("chat", "muro");
        assertThat(creado.actualizadoPor().nombre()).isEqualTo("Dueño");
        CambioAuditado fila = auditoria.filas.getFirst();
        assertThat(fila.tipo()).isEqualTo(TipoDeCambio.ROL_PERSONALIZADO);
        assertThat(fila.clave()).isEqualTo("rp:" + rolId);
        assertThat(fila.antes()).isNull();
        assertThat(fila.despues()).containsEntry("nombre", "Mentor junior").containsEntry("rolBase", "MENTOR")
                .containsEntry("activo", true).containsEntry("funcionalidades", List.of("chat", "muro"));
        assertThat(fila.motivo()).isEqualTo("Pedido del dueño");
        assertThat(fila.dispositivo()).isEqualTo("App/1.6");
    }

    @Test
    @DisplayName("editar: sin versión 400; la base sale del rol si no viene; antes y después en la auditoría")
    void editar() {
        when(finder.porId(rolId)).thenReturn(Optional.of(rol(true, 2, Set.of("muro"))));
        assertThatThrownBy(() -> service.editar(new EditarRolEnConsolaCommand(acceso, rolId, "Mentor junior", null,
                null, List.of("muro"), true, "m", null, null))).isInstanceOf(IllegalArgumentException.class);
        when(gestion.editar(any())).thenReturn(new CambioDeRolPersonalizado(rol(true, 2, Set.of("muro")),
                rol(false, 2, Set.of("muro"))));

        service.editar(new EditarRolEnConsolaCommand(acceso, rolId, "Mentor junior", null, null, List.of("muro"),
                false, "Desactivarlo", 1L, null));

        ArgumentCaptor<EditarRolPersonalizadoCommand> enviado = ArgumentCaptor.forClass(EditarRolPersonalizadoCommand.class);
        verify(gestion).editar(enviado.capture());
        assertThat(enviado.getValue().rolBase()).as("sin rolBase no se pide un cambio de base").isNull();
        assertThat(enviado.getValue().activo()).isFalse();
        assertThat(auditoria.filas.getFirst().antes()).containsEntry("activo", true);
        assertThat(auditoria.filas.getFirst().despues()).containsEntry("activo", false);
    }

    @Test
    @DisplayName("borrar: con personas 409 sin pedir contraseña; sin X-Reautenticacion 428 y no se borra nada")
    void borrar() {
        when(finder.porId(rolId)).thenReturn(Optional.of(rol(true, 3, Set.of())));
        String token = tokens.emitirDeReautenticacion(soporte, SESION, Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.borrar(new BorrarRolEnConsolaCommand(acceso, rolId, "m", token, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tokens.reautenticacion).as("el token no se gastó").containsKey(token);

        when(finder.porId(rolId)).thenReturn(Optional.of(rol(true, 0, Set.of())));
        assertThatThrownBy(() -> service.borrar(new BorrarRolEnConsolaCommand(acceso, rolId, "m", null, null)))
                .isInstanceOf(ReautenticacionRequeridaException.class);
        verify(gestion, org.mockito.Mockito.never()).borrar(any());

        when(gestion.borrar(any())).thenReturn(new CambioDeRolPersonalizado(rol(true, 0, Set.of()), null));
        service.borrar(new BorrarRolEnConsolaCommand(acceso, rolId, "Ya no se usa", token, null));

        assertThat(auditoria.filas.getFirst().antes()).containsEntry("nombre", "Mentor junior");
        assertThat(auditoria.filas.getFirst().despues()).isNull();
        assertThat(tokens.reautenticacion).doesNotContainKey(token);
    }

    @Test
    @DisplayName("reemplazar: destino inválido 400 antes de la contraseña; sin ella 428; con ella mueve y audita")
    void reemplazar() {
        UUID otra = UUID.randomUUID();
        when(finder.porId(rolId)).thenReturn(Optional.of(rol(true, 4, Set.of())));
        when(finder.porId(otra)).thenReturn(Optional.of(new RolPersonalizadoResumen(otra, "rp:" + otra,
                "Guía inicial", null, UserRole.TRAINEE_GUIDE, true, Set.of(), 0, 1, soporte, CLOCK.now())));
        String token = tokens.emitirDeReautenticacion(soporte, SESION, Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.reemplazar(new ReemplazarRolEnConsolaCommand(acceso, rolId, otra, "m", token,
                null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reemplazar(new ReemplazarRolEnConsolaCommand(acceso, rolId, null, "m", null,
                null))).isInstanceOf(ReautenticacionRequeridaException.class);

        when(gestion.reemplazarEnBloque(any())).thenReturn(new ReemplazoEnBloque(rol(true, 4, Set.of()), null, 4));
        assertThat(service.reemplazar(new ReemplazarRolEnConsolaCommand(acceso, rolId, null, "Fin del piloto",
                token, null))).isEqualTo(4);

        CambioAuditado fila = auditoria.filas.getFirst();
        assertThat(fila.antes()).containsEntry("personas", 4L);
        assertThat(fila.despues()).containsEntry("personas", 0L).containsEntry("personasMovidas", 4)
                .containsEntry("reemplazadoPor", null);
    }

    @Test
    @DisplayName("un rol que no existe: 404")
    void noExiste() {
        when(finder.porId(rolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrar(new BorrarRolEnConsolaCommand(acceso, rolId, "m", "t", null)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("listar: con quien lo cambió y solo las claves que siguen en el catálogo")
    void listar() {
        when(finder.todos()).thenReturn(List.of(rol(true, 1, Set.of("muro", "clave.vieja"))));

        List<RolPersonalizadoEnConsola> lista = service.listar(acceso);

        assertThat(lista).singleElement().satisfies(r -> {
            assertThat(r.funcionalidades()).containsExactly("muro");
            assertThat(r.personas()).isEqualTo(1);
            assertThat(r.actualizadoPor().nombre()).isEqualTo("Dueño");
        });
    }
}
