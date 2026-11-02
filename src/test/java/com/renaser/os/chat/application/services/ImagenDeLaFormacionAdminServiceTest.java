package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.AdministrarImagenDeLaFormacionUseCase.ImagenEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase.UrlDeSubida;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort.Imagen;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
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
import java.util.NoSuchElementException;
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

/** Ver y cambiar la imagen de una formación desde la app (spec 035). Sin Spring: la bitácora en memoria y la revisión de la imagen de doble. */
class ImagenDeLaFormacionAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID ID = UUID.fromString("44444444-dddd-4ddd-8ddd-444444444444");
    private static final String RUTA = "formaciones/imagenes/" + ID;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 5};

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final AlmacenamientoPort almacenamiento = mock(AlmacenamientoPort.class);
    private final ImagenDeLaFormacionPort imagenes = mock(ImagenDeLaFormacionPort.class);
    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private ImagenDeLaFormacionAdminService servicio;

    @BeforeEach
    void armar() {
        ImagenDeLaFormacionService consulta = new ImagenDeLaFormacionService(cambios, imagenes);
        servicio = new ImagenDeLaFormacionAdminService(
                new BienvenidaParaAdministrar((formacion, pieza) -> "Hola, {nombre}.", cambios, usuarios, almacenamiento, true),
                cambios, imagenes, consulta, almacenamiento, usuarios, () -> ID, FixedClock.at(AHORA));
        lenient().when(almacenamiento.guardaObjetos()).thenReturn(true);
        lenient().when(almacenamiento.firmarSubida(anyString(), anyString(), any()))
                .thenAnswer(inv -> URI.create("https://almacen.test/" + inv.getArgument(0)));
        lenient().when(imagenes.jpegDe(anyString())).thenReturn(new Imagen(JPEG, "h"));
    }

    @Test
    @DisplayName("la URL de subida es para una ruta nueva de imágenes de formaciones, firmada con el tipo pedido")
    void urlDeSubida() {
        UrlDeSubida subida = servicio.solicitarSubida(admin(), "image/png");

        assertThat(subida.ruta()).isEqualTo(RUTA);
        verify(almacenamiento).firmarSubida(RUTA, "image/png", Duration.ofMinutes(10));
        assertThatThrownBy(() -> servicio.solicitarSubida(admin(), "image/gif")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmar revisa la imagen y la deja vigente SOLO para esa formación, con quién y cuándo")
    void confirmar() {
        UserId kelin = admin();

        ImagenEditable editable = servicio.confirmar(kelin, Formacion.DIPLOMADO, RUTA);

        verify(imagenes).revisar(RUTA);
        assertThat(cambios.filas()).containsExactly(new CambioDeBienvenida(Formacion.DIPLOMADO,
                com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION, RUTA, kelin, AHORA));
        assertThat(editable.hayImagen()).isTrue();
        assertThat(editable.cambiada()).isTrue();
        assertThat(editable.heredada()).isFalse();
        assertThat(editable.ultimoCambio().en()).isEqualTo(AHORA);
        ImagenEditable laEscuela = servicio.ver(kelin, Formacion.ESCUELA);
        assertThat(laEscuela.hayImagen()).as("la Escuela no se entera").isFalse();
        assertThat(laEscuela.cambiada()).isFalse();
    }

    @Test
    @DisplayName("una formación sin imagen propia dice que usa la de la Escuela, si la Escuela tiene")
    void heredada() {
        UserId kelin = admin();
        servicio.confirmar(kelin, Formacion.ESCUELA, RUTA);

        ImagenEditable delDiplomado = servicio.ver(kelin, Formacion.DIPLOMADO);

        assertThat(delDiplomado.hayImagen()).isTrue();
        assertThat(delDiplomado.cambiada()).isFalse();
        assertThat(delDiplomado.heredada()).isTrue();
        assertThat(servicio.ver(kelin, Formacion.ESCUELA).heredada()).as("la Escuela nunca hereda").isFalse();
    }

    @Test
    @DisplayName("una imagen que no pasa la revisión no queda vigente")
    void laQueNoPasaNoQueda() {
        doThrow(new IllegalArgumentException("La imagen es muy chica")).when(imagenes).revisar(RUTA);

        assertThatThrownBy(() -> servicio.confirmar(admin(), Formacion.DIPLOMADO, RUTA)).hasMessage("La imagen es muy chica");
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("una ruta que no es de imágenes de formaciones se rechaza sin abrir nada (ni portadas ni evidencias)")
    void rutaAjena() {
        assertThatThrownBy(() -> servicio.confirmar(admin(), Formacion.DIPLOMADO, "bienvenida/portadas/abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.vista(admin(), Formacion.DIPLOMADO, "evidencia-habitos/x")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(imagenes);
    }

    @Test
    @DisplayName("sin almacenamiento de verdad (local) no hay imagen que abrir: 409 y no queda nada")
    void sinAlmacenamiento() {
        when(almacenamiento.guardaObjetos()).thenReturn(false);

        assertThatThrownBy(() -> servicio.confirmar(admin(), Formacion.DIPLOMADO, RUTA)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene dónde guardar imágenes");
        assertThat(servicio.ver(admin(), Formacion.DIPLOMADO).sePuedeCambiar()).isFalse();
        assertThat(cambios.filas()).isEmpty();
        verifyNoInteractions(imagenes);
    }

    @Test
    @DisplayName("confirmar la que ya es propia no escribe nada; volver solo si había una propia; y vuelve a la de la Escuela")
    void sinCambiosRepetidos() {
        UserId kelin = admin();
        servicio.volverALaOriginal(kelin, Formacion.DIPLOMADO);
        assertThat(cambios.filas()).isEmpty();

        servicio.confirmar(kelin, Formacion.DIPLOMADO, RUTA);
        servicio.confirmar(kelin, Formacion.DIPLOMADO, RUTA);
        ImagenEditable vuelta = servicio.volverALaOriginal(kelin, Formacion.DIPLOMADO);

        assertThat(cambios.filas()).hasSize(2);
        assertThat(vuelta.cambiada()).isFalse();
        assertThat(vuelta.hayImagen()).as("la Escuela no tiene: vuelve a la de la app").isFalse();
        assertThat(vuelta.ultimoCambio().volvioAlOriginal()).isTrue();
    }

    @Test
    @DisplayName("la vista previa: la vigente de la formación, la candidata (revisada), o nada si no hay ninguna")
    void vistaPrevia() {
        UserId kelin = admin();

        assertThat(servicio.vista(kelin, Formacion.DIPLOMADO, null)).as("sin imagen: la app muestra la suya").isNull();

        assertThat(servicio.vista(kelin, Formacion.DIPLOMADO, RUTA)).isEqualTo(JPEG);
        verify(imagenes).revisar(RUTA);

        servicio.confirmar(kelin, Formacion.DIPLOMADO, RUTA);
        assertThat(servicio.vista(kelin, Formacion.DIPLOMADO, null)).isEqualTo(JPEG);
        assertThat(servicio.vista(kelin, Formacion.ESCUELA, null)).isNull();

        doThrow(new NoSuchElementException("sin nada subido")).when(imagenes).revisar(RUTA + "-2");
        assertThatThrownBy(() -> servicio.vista(kelin, Formacion.DIPLOMADO, RUTA + "-2")).isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("autorización negativa: ningún otro rol ve, sube, confirma, vuelve ni ve la vista previa")
    void otroRolNo(UserRole rol) {
        UserId actor = cuenta(rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.ver(actor, Formacion.DIPLOMADO)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.solicitarSubida(actor, "image/png")).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.confirmar(actor, Formacion.DIPLOMADO, RUTA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.volverALaOriginal(actor, Formacion.DIPLOMADO)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.vista(actor, Formacion.DIPLOMADO, null)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(imagenes);
        verify(almacenamiento, never()).firmarSubida(anyString(), anyString(), any());
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("autorización negativa: un ADMIN SUSPENDIDO recibe 403, y Alquimista puede")
    void suspendidoNoYAlquimistaSi() {
        UserId suspendido = cuenta(UserRole.ADMIN, UserStatus.SUSPENDED);
        UserId alquimista = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.confirmar(suspendido, Formacion.DIPLOMADO, RUTA)).isInstanceOf(NotAuthorizedException.class);
        assertThat(servicio.confirmar(alquimista, Formacion.DIPLOMADO, RUTA).cambiada()).isTrue();
    }

    private UserId admin() {
        return cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
    }

    private UserId cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        UserSummary resumen = new UserSummary(id, "Kelin Rojas", null, rol, estado);
        lenient().when(usuarios.findById(id)).thenReturn(Optional.of(resumen));
        lenient().when(usuarios.findByIds(any())).thenReturn(java.util.Map.of());
        return id;
    }
}
