package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.out.eliminacion.BorrarDatosPropiosDeCuentaPort;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El borrado definitivo (D-243) orquesta a los modulos y no conoce ninguna tabla ajena. Lo que se
 * prueba aca es la POLITICA: que archivos se borran, en que orden, y que pasa si algo falla. Que las
 * filas desaparezcan de verdad lo prueba {@code EliminacionDeCuentaIT} contra Postgres.
 */
@ExtendWith(MockitoExtension.class)
class BorradoDefinitivoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private BorrarDatosPropiosDeCuentaPort borrarDatosPropios;
    @Mock
    private RegistrarEliminacionPort registrarEliminacion;
    @Mock
    private AlmacenamientoPort almacenamiento;
    @Mock
    private CerrarTodasLasSesionesUseCase cerrarSesiones;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private BorradoDeDatosDeCuenta moduloA;
    @Mock
    private BorradoDeDatosDeCuenta moduloB;

    private final UserId id = UserId.of(UUID.randomUUID());
    private User cuenta;
    private BorradoDefinitivoService service;

    @BeforeEach
    void setUp() {
        cuenta = User.rehydrate(id, new Email("se-va-" + id + "@renaser.dev"), UserRole.TRAINEE, UserStatus.SUSPENDED,
                "Se Va", "https://bucket/avatares/" + id, null, null, null, Instant.parse("2026-09-01T00:00:00Z"));
        service = new BorradoDefinitivoService(loadUserPort, List.of(moduloA, moduloB), borrarDatosPropios,
                registrarEliminacion, almacenamiento, cerrarSesiones, transactionManager, CLOCK);
    }

    @Test
    @DisplayName("borra lo de cada modulo, despues lo de users, y deja la auditoria sin datos personales")
    void borraModulosYDespuesUsers() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));
        UserId admin = UserId.of(UUID.randomUUID());

        boolean borrada = service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, admin);

        assertThat(borrada).isTrue();
        InOrder orden = inOrder(cerrarSesiones, moduloA, moduloB, borrarDatosPropios, registrarEliminacion);
        orden.verify(cerrarSesiones).cerrarTodas(id);
        orden.verify(moduloA).borrarDatosDe(id);
        orden.verify(moduloB).borrarDatosDe(id);
        orden.verify(borrarDatosPropios).borrarTodoDe(id, cuenta.email());
        ArgumentCaptor<RegistroDeEliminacion> registro = ArgumentCaptor.forClass(RegistroDeEliminacion.class);
        orden.verify(registrarEliminacion).registrar(registro.capture());
        assertThat(registro.getValue().cuentaId()).isEqualTo(id);
        assertThat(registro.getValue().actorId()).isEqualTo(admin);
        assertThat(registro.getValue().accion()).isEqualTo(RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN);
        assertThat(registro.getValue().rol()).isEqualTo(UserRole.TRAINEE);
    }

    @Test
    @DisplayName("una cuenta que ya no existe no es un error: no hace nada")
    void idempotente() {
        when(loadUserPort.byId(id)).thenReturn(Optional.empty());

        assertThat(service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null)).isFalse();
        verify(borrarDatosPropios, never()).borrarTodoDe(any(), any());
        verify(moduloA, never()).borrarDatosDe(any());
    }

    @Test
    @DisplayName("borra del bucket lo exclusivo y deja lo que otra fila que queda sigue usando")
    void archivosExclusivosYCompartidos() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));
        String evidencia = "evidencia-habitos/" + id + "/reg-1/foto";
        String fotoCompartida = "muro/fotos/" + id + "/compartida";
        String fotoDelChat = "chat/" + UUID.randomUUID() + "/fotos/1";
        when(moduloA.archivosDe(id)).thenReturn(Set.of(evidencia, fotoCompartida));
        when(moduloB.archivosDe(id)).thenReturn(Set.of(fotoDelChat));
        when(moduloB.archivosEnUsoTrasBorrar(any(), any())).thenReturn(Set.of(fotoCompartida));
        List<String> borrados = new ArrayList<>();
        AlmacenamientoPort registrando = registrando(borrados);
        service = new BorradoDefinitivoService(loadUserPort, List.of(moduloA, moduloB), borrarDatosPropios,
                registrarEliminacion, registrando, cerrarSesiones, transactionManager, CLOCK);

        service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);

        assertThat(borrados).containsExactlyInAnyOrder("avatares/" + id, evidencia, fotoDelChat);
    }

    @Test
    @DisplayName("una ruta que no es de esta cuenta por su forma no se borra, aunque un modulo la declare")
    void rutasAjenasNoSeBorran() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));
        UUID otro = UUID.randomUUID();
        when(moduloA.archivosDe(id)).thenReturn(Set.of("avatares/" + otro, "muro/fotos/" + otro + "/x",
                "calendar/portadas/evento-1"));

        service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);

        verify(almacenamiento).borrar("avatares/" + id);
        verify(almacenamiento, never()).borrar("avatares/" + otro);
        verify(almacenamiento, never()).borrar("muro/fotos/" + otro + "/x");
        verify(almacenamiento, never()).borrar("calendar/portadas/evento-1");
    }

    @Test
    @DisplayName("los objetos se borran ANTES que las filas: si se corta en el medio, se reintenta entero")
    void objetosAntesQueFilas() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));

        service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null);

        InOrder orden = inOrder(almacenamiento, borrarDatosPropios);
        orden.verify(almacenamiento).borrar("avatares/" + id);
        orden.verify(borrarDatosPropios).borrarTodoDe(any(), any());
    }

    @Test
    @DisplayName("un fallo de S3 no frena el borrado de la cuenta")
    void falloDeS3NoFrena() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));
        doThrow(new RuntimeException("S3 caido")).when(almacenamiento).borrar(any());

        assertThat(service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null)).isTrue();
        verify(borrarDatosPropios).borrarTodoDe(id, cuenta.email());
    }

    @Test
    @DisplayName("si un modulo falla al borrar, la excepcion sale (y la transaccion se deshace): nada a medias")
    void falloDeUnModuloSale() {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(cuenta));
        doThrow(new IllegalStateException("FK inesperada")).when(moduloB).borrarDatosDe(id);

        assertThatThrownBy(() -> service.borrar(id, RegistroDeEliminacion.Accion.ELIMINADA_AL_VENCER, null))
                .isInstanceOf(IllegalStateException.class);
        verify(borrarDatosPropios, never()).borrarTodoDe(any(), any());
        verify(registrarEliminacion, never()).registrar(any());
    }

    private static AlmacenamientoPort registrando(List<String> borrados) {
        AlmacenamientoPort doble = mock(AlmacenamientoPort.class);
        org.mockito.Mockito.doAnswer(inv -> borrados.add(inv.getArgument(0))).when(doble).borrar(any());
        return doble;
    }
}
