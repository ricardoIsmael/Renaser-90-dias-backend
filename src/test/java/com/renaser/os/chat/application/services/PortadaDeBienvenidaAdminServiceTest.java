package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase.UrlDeSubida;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.PortadaDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cambiar la portada de la tarjeta y verla antes de usarla (D-210). Sin Spring: la bitácora en memoria,
 * la revisión de la imagen y el dibujo de dobles.
 */
class PortadaDeBienvenidaAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final UUID ID = UUID.fromString("33333333-cccc-4ccc-8ccc-333333333333");
    private static final String RUTA = "bienvenida/portadas/" + ID;
    private static final byte[] TARJETA = {(byte) 0xFF, (byte) 0xD8, 9};

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final AlmacenamientoPort almacenamiento = mock(AlmacenamientoPort.class);
    private final PortadaDeBienvenidaPort portadas = mock(PortadaDeBienvenidaPort.class);
    private final DibujarBienvenidaPort dibujante = mock(DibujarBienvenidaPort.class);
    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private PortadaDeBienvenidaAdminService servicio;

    @BeforeEach
    void armar() {
        servicio = new PortadaDeBienvenidaAdminService(
                new BienvenidaParaAdministrar(pieza -> "Hola, {nombre}.", cambios, usuarios, almacenamiento, true),
                cambios, portadas, dibujante, almacenamiento, () -> ID, FixedClock.at(AHORA));
        lenient().when(almacenamiento.guardaObjetos()).thenReturn(true);
        lenient().when(usuarios.findByIds(any())).thenReturn(Map.of());
        lenient().when(almacenamiento.firmarSubida(anyString(), anyString(), any()))
                .thenAnswer(inv -> URI.create("https://almacen.test/" + inv.getArgument(0)));
        lenient().when(dibujante.dibujar(anyString())).thenReturn(TARJETA);
        lenient().when(dibujante.dibujar(anyString(), anyString())).thenReturn(TARJETA);
    }

    @Test
    @DisplayName("la URL de subida es para una ruta nueva de portadas, firmada con el tipo pedido")
    void urlDeSubida() {
        UrlDeSubida subida = servicio.solicitarSubida(admin(), "image/jpeg");

        assertThat(subida.ruta()).isEqualTo(RUTA);
        assertThat(subida.url()).hasToString("https://almacen.test/" + RUTA);
        verify(almacenamiento).firmarSubida(RUTA, "image/jpeg", Duration.ofMinutes(10));
        assertThatThrownBy(() -> servicio.solicitarSubida(admin(), "image/gif")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmar revisa la imagen y la deja vigente, con quién y cuándo")
    void confirmar() {
        UserId kelin = admin();

        BienvenidaEditable bienvenida = servicio.confirmar(kelin, RUTA);

        verify(portadas).revisar(RUTA);
        assertThat(cambios.filas()).containsExactly(new CambioDeBienvenida(PiezaDeBienvenida.PORTADA, RUTA, kelin, AHORA));
        assertThat(bienvenida.portada().cambiada()).isTrue();
        assertThat(bienvenida.portada().ultimoCambio().en()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("una imagen que no pasa la revisión no queda vigente")
    void laQueNoPasaNoQueda() {
        doThrow(new IllegalArgumentException("El nombre no se leería")).when(portadas).revisar(RUTA);

        assertThatThrownBy(() -> servicio.confirmar(admin(), RUTA)).hasMessage("El nombre no se leería");
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("una ruta que no es de portadas se rechaza sin abrir nada")
    void rutaAjena() {
        assertThatThrownBy(() -> servicio.confirmar(admin(), "firmas/ana/pacto.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.muestra(admin(), "Ana", "evidencia-habitos/x")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(portadas);
    }

    @Test
    @DisplayName("sin almacenamiento de verdad (local) no hay portada que abrir: 409 y no queda nada")
    void sinAlmacenamiento() {
        when(almacenamiento.guardaObjetos()).thenReturn(false);

        assertThatThrownBy(() -> servicio.confirmar(admin(), RUTA)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene dónde guardar imágenes");
        assertThat(servicio.volverALaOriginal(admin()).portada().sePuedeCambiar()).isFalse();
        assertThat(cambios.filas()).isEmpty();
        verifyNoInteractions(portadas);
    }

    @Test
    @DisplayName("confirmar la que ya es vigente no escribe nada; volver a la original solo si estaba cambiada")
    void sinCambiosRepetidos() {
        UserId kelin = admin();
        servicio.volverALaOriginal(kelin);
        assertThat(cambios.filas()).isEmpty();

        servicio.confirmar(kelin, RUTA);
        servicio.confirmar(kelin, RUTA);
        BienvenidaEditable bienvenida = servicio.volverALaOriginal(kelin);

        assertThat(cambios.filas()).hasSize(2);
        assertThat(bienvenida.portada().cambiada()).isFalse();
        assertThat(bienvenida.portada().ultimoCambio().volvioAlOriginal()).isTrue();
    }

    @Test
    @DisplayName("la vista previa usa el primer nombre, como con una persona de verdad; con candidata, la revisa")
    void vistaPrevia() {
        assertThat(servicio.muestra(admin(), "maría josé ñahui", null)).isEqualTo(TARJETA);
        verify(dibujante).dibujar("María");

        assertThat(servicio.muestra(admin(), "Ana", RUTA)).isEqualTo(TARJETA);
        verify(portadas).revisar(RUTA);
        verify(dibujante).dibujar("Ana", RUTA);

        assertThatThrownBy(() -> servicio.muestra(admin(), "x".repeat(41), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("autorización negativa: ningún otro rol sube, confirma, vuelve ni ve la tarjeta de muestra")
    void otroRolNo(UserRole rol) {
        UserId actor = cuenta(rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.solicitarSubida(actor, "image/jpeg")).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.confirmar(actor, RUTA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.volverALaOriginal(actor)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.muestra(actor, "Ana", null)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(portadas, dibujante);
        verify(almacenamiento, never()).firmarSubida(anyString(), anyString(), any());
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("autorización negativa: un ADMIN SUSPENDIDO recibe 403")
    void suspendidoNo() {
        UserId actor = cuenta(UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.confirmar(actor, RUTA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.muestra(actor, "Ana", null)).isInstanceOf(NotAuthorizedException.class);
        assertThat(cambios.filas()).isEmpty();
    }

    private UserId admin() {
        return cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
    }

    private UserId cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Kelin Rojas", null, rol, estado)));
        return id;
    }
}
