package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.ConsultarImagenDeLaFormacionUseCase.ImagenDeLaFormacionVigente;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort;
import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort.Imagen;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Qué imagen tiene cada formación en el chat (spec 035): la suya, la de la Escuela o ninguna. Sin Spring: la bitácora en memoria. */
class ImagenDeLaFormacionServiceTest {

    private static final UserId KELIN = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final Instant ANTES = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-11-02T12:00:00Z");
    private static final String DE_LA_ESCUELA = "formaciones/imagenes/escuela";
    private static final String DEL_DIPLOMADO = "formaciones/imagenes/diplomado";

    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final ImagenDeLaFormacionPort imagenes = mock(ImagenDeLaFormacionPort.class);
    private final ImagenDeLaFormacionService servicio = new ImagenDeLaFormacionService(cambios, imagenes);

    // spec 035 FR-012
    @Test
    @DisplayName("sin ninguna imagen subida no hay imagen de formación en ninguna: los chats usan la de la app, y no se abre nada")
    void sinNingunaImagen() {
        for (Formacion formacion : Formacion.values()) {
            assertThat(servicio.vigente(formacion)).isEmpty();
            assertThat(servicio.servir(formacion)).isEmpty();
        }
        verifyNoInteractions(imagenes);
    }

    // spec 035 FR-005
    @Test
    @DisplayName("si solo la Escuela subió una, el Diplomado y la Certificación la siguen, con el instante de ESA subida")
    void siguenALaEscuela() {
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.ESCUELA, DE_LA_ESCUELA, KELIN, ANTES));

        for (Formacion formacion : Formacion.values()) {
            assertThat(servicio.vigente(formacion)).contains(new ImagenDeLaFormacionVigente(DE_LA_ESCUELA, ANTES));
        }
    }

    @Test
    @DisplayName("la propia manda y no cambia a las otras; si la Escuela cambia después, solo cambian las que la siguen")
    void laPropiaManda() {
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.ESCUELA, DE_LA_ESCUELA, KELIN, ANTES));
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.DIPLOMADO, DEL_DIPLOMADO, KELIN, DESPUES));

        assertThat(servicio.vigente(Formacion.DIPLOMADO)).contains(new ImagenDeLaFormacionVigente(DEL_DIPLOMADO, DESPUES));
        assertThat(servicio.vigente(Formacion.ESCUELA)).contains(new ImagenDeLaFormacionVigente(DE_LA_ESCUELA, ANTES));
        assertThat(servicio.vigente(Formacion.CERTIFICACION)).contains(new ImagenDeLaFormacionVigente(DE_LA_ESCUELA, ANTES));

        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.ESCUELA, "formaciones/imagenes/escuela-2", KELIN, DESPUES));

        assertThat(servicio.vigente(Formacion.DIPLOMADO).orElseThrow().ruta()).isEqualTo(DEL_DIPLOMADO);
        assertThat(servicio.vigente(Formacion.CERTIFICACION).orElseThrow().ruta()).isEqualTo("formaciones/imagenes/escuela-2");
    }

    @Test
    @DisplayName("volver al original en el Diplomado es volver a la de la Escuela; en la Escuela, a la de la app")
    void volverAlOriginal() {
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.ESCUELA, DE_LA_ESCUELA, KELIN, ANTES));
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.DIPLOMADO, DEL_DIPLOMADO, KELIN, ANTES));
        cambios.registrar(CambioDeBienvenida.volverAlOriginal(Formacion.DIPLOMADO, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION, KELIN, DESPUES));

        assertThat(servicio.vigente(Formacion.DIPLOMADO).orElseThrow().ruta()).isEqualTo(DE_LA_ESCUELA);

        cambios.registrar(CambioDeBienvenida.volverAlOriginal(Formacion.ESCUELA, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION, KELIN, DESPUES));

        assertThat(servicio.vigente(Formacion.ESCUELA)).isEmpty();
        assertThat(servicio.vigente(Formacion.DIPLOMADO)).as("el Diplomado ya no tiene la suya y la Escuela volvió a la de la app").isEmpty();
    }

    // spec 035 FR-008
    @Test
    @DisplayName("servir abre la imagen vigente de ESA formación; si ya no abre, no hay imagen (sin romper la lista de chats)")
    void servir() {
        cambios.registrar(CambioDeBienvenida.imagenDeLaFormacion(Formacion.DIPLOMADO, DEL_DIPLOMADO, KELIN, ANTES));
        when(imagenes.jpegDe(DEL_DIPLOMADO)).thenReturn(new Imagen(new byte[] {(byte) 0xFF, (byte) 0xD8}, "abc"));

        assertThat(servicio.servir(Formacion.DIPLOMADO)).hasValueSatisfying(imagen -> assertThat(imagen.huella()).isEqualTo("abc"));
        assertThat(servicio.servir(Formacion.ESCUELA)).as("la Escuela no ve la del Diplomado").isEmpty();
        verify(imagenes).jpegDe(DEL_DIPLOMADO);

        when(imagenes.jpegDe(anyString())).thenThrow(new NoSuchElementException("ya no está"));

        assertThat(servicio.servir(Formacion.DIPLOMADO)).isEmpty();
    }
}
