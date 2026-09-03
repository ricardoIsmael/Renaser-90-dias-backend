package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** wall/schema.ts:41-54 (carrusel obligatorio, 1 a 10) y service.ts:220-224 (ocultar no
 * destruye). */
class PublicacionTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final PublicacionId ID = PublicacionId.of(UUID.randomUUID());

    private static MediaPublicacion foto() {
        return new MediaPublicacion("wall", "muro/x/1.jpg", "image/jpeg", 0);
    }

    private static Publicacion nueva() {
        return Publicacion.publicar(ID, UserId.of(UUID.randomUUID()), "Hola comunidad", List.of(foto()), null,
                CLOCK.now());
    }

    @Test
    void publicarSiempreEsManual() {
        Publicacion p = nueva();
        assertThat(p.id()).isEqualTo(ID);
        assertThat(p.tipo()).isEqualTo(TipoPublicacion.MANUAL);
    }

    @Test
    void sinMediaEsInvalido() {
        assertThatThrownBy(() -> Publicacion.publicar(ID, UserId.of(UUID.randomUUID()), "texto", List.of(), null,
                CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masDeDiezArchivosEsInvalido() {
        List<MediaPublicacion> media = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> new MediaPublicacion("wall", "muro/x/" + i, "image/jpeg", i)).toList();
        assertThatThrownBy(() -> Publicacion.publicar(ID, UserId.of(UUID.randomUUID()), "texto", media, null,
                CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textoVacioEsInvalido() {
        assertThatThrownBy(() -> Publicacion.publicar(ID, UserId.of(UUID.randomUUID()), "   ", List.of(foto()), null,
                CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ocultarLaMarcaOcultaSinBorrarNada() {
        Publicacion p = nueva();
        p.ocultar(CLOCK.now());
        assertThat(p.oculta()).isTrue();
        assertThat(p.texto()).isEqualTo("Hola comunidad");
    }

    @Test
    void editarUnaOcultaFalla() {
        Publicacion p = nueva();
        p.ocultar(CLOCK.now());
        assertThatThrownBy(() -> p.editar("nuevo texto", List.of(foto()), CLOCK.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restaurarUnaVisibleFalla() {
        Publicacion p = nueva();
        assertThatThrownBy(() -> p.restaurar(CLOCK.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restaurarUnaOcultaLaDejaVisible() {
        Publicacion p = nueva();
        p.ocultar(CLOCK.now());
        p.restaurar(CLOCK.now());
        assertThat(p.oculta()).isFalse();
    }

    /** Hueco #17 (docs/MODULO_ROCKS.md sec. 11.2): entrada para community.api.PublicarEnMuroPort. */
    @Test
    void publicarAutomaticaEsHitoAutomaticoYSinCategoria() {
        Publicacion p = Publicacion.publicarAutomatica(ID, UserId.of(UUID.randomUUID()),
                "Complete mi Roca: Meditar", List.of(foto()), CLOCK.now());
        assertThat(p.tipo()).isEqualTo(TipoPublicacion.HITO_AUTOMATICO);
        assertThat(p.categoriaClave()).isNull();
        assertThat(p.oculta()).isFalse();
    }

    @Test
    void publicarAutomaticaSinMediaEsInvalido() {
        assertThatThrownBy(() -> Publicacion.publicarAutomatica(ID, UserId.of(UUID.randomUUID()), "texto",
                List.of(), CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
