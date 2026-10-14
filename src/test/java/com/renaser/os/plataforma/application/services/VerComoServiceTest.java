package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.AbrirVerComoCommand;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.CerrarVerComoCommand;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.VerComoAbierto;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.SesionesDeVerComoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.plataforma.domain.model.vercomo.RolElegido;
import com.renaser.os.plataforma.domain.model.vercomo.SesionDeVerComo;
import com.renaser.os.shared.domain.ConsolaBloqueadaException;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.CuentasDePruebaUseCase;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** «Ver como rol» en la plataforma (contrato fase-2.md §5; D-270): cerrojos, orden y auditoría. Sin Spring. */
@ExtendWith(MockitoExtension.class)
class VerComoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Mock
    private GuardiaDeSoporteTi guardia;
    @Mock
    private CuentasDePruebaUseCase cuentas;
    @Mock
    private EntornoDeLaCuentaDePrueba entorno;
    @Mock
    private SesionesDeVerComoPort sesiones;
    @Mock
    private RegistrarCambioAuditadoPort auditoria;
    @Mock
    private IdGenerator idGenerator;

    private VerComoService servicio;
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final AccesoDeConsola acceso = new AccesoDeConsola(soporte, "consola", "sesion-de-soporte");

    @BeforeEach
    void armar() {
        servicio = new VerComoService(guardia, cuentas, entorno, sesiones, auditoria, idGenerator,
                FixedClock.at(AHORA));
        lenient().when(idGenerator.newId()).thenAnswer(i -> UUID.randomUUID());
    }

    @Test
    @DisplayName("abre: la cuenta del rol, su entorno, la auditoría de entrada y recién al final la sesión nueva")
    void abre() {
        CuentaDePrueba mentor = new CuentaDePrueba(UserId.of(UUID.randomUUID()), "Prueba · Mentor", UserRole.MENTOR,
                null);
        when(cuentas.deRol(soporte, UserRole.MENTOR)).thenReturn(mentor);
        when(sesiones.abrir(any())).thenReturn("token-nuevo");

        VerComoAbierto abierto = servicio.abrir(new AbrirVerComoCommand(acceso, "MENTOR", null, "App/1.6"));

        assertThat(abierto.token()).isEqualTo("token-nuevo");
        assertThat(abierto.cuenta()).isEqualTo(mentor);
        assertThat(abierto.venceEn()).isEqualTo(AHORA.plusSeconds(1800));
        InOrder orden = inOrder(guardia, cuentas, entorno, auditoria, sesiones);
        orden.verify(guardia).exigirConsolaAbierta(acceso);
        orden.verify(cuentas).deRol(soporte, UserRole.MENTOR);
        orden.verify(entorno).preparar(soporte, mentor);
        orden.verify(auditoria).registrar(any());
        orden.verify(sesiones).abrir(any());
        ArgumentCaptor<SesionDeVerComo> sesion = ArgumentCaptor.forClass(SesionDeVerComo.class);
        verify(sesiones).abrir(sesion.capture());
        assertThat(sesion.getValue().cuenta()).isEqualTo(mentor.id());
        assertThat(sesion.getValue().abiertaPor()).isEqualTo(soporte);
        ArgumentCaptor<CambioAuditado> fila = ArgumentCaptor.forClass(CambioAuditado.class);
        verify(auditoria).registrar(fila.capture());
        assertThat(fila.getValue().tipo()).isEqualTo(TipoDeCambio.VER_COMO);
        assertThat(fila.getValue().antes()).isNull();
        assertThat(fila.getValue().despues()).isEqualTo(Map.of("rol", "MENTOR"));
        assertThat(fila.getValue().dispositivo()).isEqualTo("App/1.6");
    }

    @Test
    @DisplayName("con un rol personalizado audita {rolPersonalizado: {id, nombre}}")
    void rolPersonalizado() {
        UUID rol = UUID.randomUUID();
        when(cuentas.deRolPersonalizado(soporte, rol)).thenReturn(new CuentaDePrueba(UserId.of(UUID.randomUUID()),
                "Prueba · Mentor junior", UserRole.MENTOR, new RolPersonalizadoAsignado(rol, "Mentor junior")));
        when(sesiones.abrir(any())).thenReturn("token");

        servicio.abrir(new AbrirVerComoCommand(acceso, null, rol, null));

        ArgumentCaptor<CambioAuditado> fila = ArgumentCaptor.forClass(CambioAuditado.class);
        verify(auditoria).registrar(fila.capture());
        assertThat(fila.getValue().despues()).isEqualTo(Map.of("rolPersonalizado",
                Map.of("id", rol.toString(), "nombre", "Mentor junior")));
    }

    @Test
    @DisplayName("sin Soporte TI (403) o con la consola bloqueada (423) no crea nada")
    void cerrojos() {
        doThrow(new NotAuthorizedException("Solo Soporte TI")).when(guardia).exigirConsolaAbierta(acceso);
        assertThatThrownBy(() -> servicio.abrir(new AbrirVerComoCommand(acceso, "MENTOR", null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        doThrow(new ConsolaBloqueadaException()).when(guardia).exigirConsolaAbierta(acceso);
        assertThatThrownBy(() -> servicio.abrir(new AbrirVerComoCommand(acceso, "MENTOR", null, null)))
                .isInstanceOf(ConsolaBloqueadaException.class);

        verifyNoInteractions(cuentas, entorno, sesiones, auditoria);
    }

    @Test
    @DisplayName("un pedido inválido es 400 antes de crear cuentas")
    void pedidoInvalido() {
        assertThatThrownBy(() -> servicio.abrir(new AbrirVerComoCommand(acceso, "IT_SUPPORT", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cuentas, sesiones, auditoria);
    }

    @Test
    @DisplayName("cerrar audita la salida a nombre de quien la abrió y borra la sesión")
    void cerrar() {
        SesionDeVerComo abierta = SesionDeVerComo.abrir(UserId.of(UUID.randomUUID()), soporte,
                new RolElegido.Base(UserRole.ADMIN), AHORA.minusSeconds(60));
        when(sesiones.leer("token")).thenReturn(Optional.of(abierta));

        servicio.cerrar(new CerrarVerComoCommand("token", "App/1.6"));

        ArgumentCaptor<CambioAuditado> fila = ArgumentCaptor.forClass(CambioAuditado.class);
        verify(auditoria).registrar(fila.capture());
        assertThat(fila.getValue().actor()).isEqualTo(soporte);
        assertThat(fila.getValue().antes()).isEqualTo(Map.of("rol", "ADMIN"));
        assertThat(fila.getValue().despues()).isNull();
        verify(sesiones).cerrar("token");
    }

    @Test
    @DisplayName("cerrar con una sesión que no es de «ver como» (o sin sesión) es 404 y no cierra nada")
    void cerrarSinVerComo() {
        when(sesiones.leer("comun")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cerrar(new CerrarVerComoCommand("comun", null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.cerrar(new CerrarVerComoCommand(null, null)))
                .isInstanceOf(NoSuchElementException.class);
        verify(sesiones, never()).cerrar(any());
        verifyNoInteractions(auditoria);
    }
}
