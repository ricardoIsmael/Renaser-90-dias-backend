package com.renaser.os.phasecontracts.domain.model.animal;

import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnimalDeFaseTest {

    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-10-06T03:00:00Z");

    @Test
    @DisplayName("sin personalizar: ni imagen ni nombre, la app usa los suyos")
    void sinPersonalizar() {
        AnimalDeFase a = AnimalDeFase.sinPersonalizar(FasePrograma.FASE_2_DESARROLLO);
        assertThat(a.tieneImagenPropia()).isFalse();
        assertThat(a.nombre()).isNull();
    }

    @Test
    @DisplayName("usar una imagen guarda la ruta y quién la puso; restaurar la quita")
    void usarYRestaurar() {
        AnimalDeFase a = AnimalDeFase.sinPersonalizar(FasePrograma.FASE_1_RENACER);
        a.usarImagen("fases/animales/1/abc", ACTOR, AHORA);
        assertThat(a.rutaImagen()).isEqualTo("fases/animales/1/abc");
        assertThat(a.actualizadoPor()).isEqualTo(ACTOR);
        a.restaurarImagen(ACTOR, AHORA);
        assertThat(a.tieneImagenPropia()).isFalse();
    }

    @Test
    @DisplayName("una ruta ajena (evidencias, otra carpeta, con ..) no se acepta")
    void rutaAjena() {
        AnimalDeFase a = AnimalDeFase.sinPersonalizar(FasePrograma.FASE_1_RENACER);
        for (String ruta : new String[]{null, "", "evidencias/x", "fases/animales/", "fases/animales/../x"}) {
            assertThatThrownBy(() -> a.usarImagen(ruta, ACTOR, AHORA)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("el nombre se limpia, uno vacío vuelve al de la app y uno largo se rechaza")
    void nombre() {
        AnimalDeFase a = AnimalDeFase.sinPersonalizar(FasePrograma.FASE_4_ASCENSION);
        a.nombrar("  Águila   real ", ACTOR, AHORA);
        assertThat(a.nombre()).isEqualTo("Águila real");
        a.nombrar("   ", ACTOR, AHORA);
        assertThat(a.nombre()).isNull();
        assertThatThrownBy(() -> a.nombrar("x".repeat(25), ACTOR, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("peso y medidas: 2 MB, entre 256 y 4096 px")
    void medidas() {
        CabeceraDeImagen ok = new CabeceraDeImagen("png", 800, 800);
        ImagenDeAnimal.exigirQueSePuedaUsar(ok, 1000);
        assertThatThrownBy(() -> ImagenDeAnimal.exigirQueSePuedaUsar(ok, 3L * 1024 * 1024)).hasMessageContaining("pesa");
        assertThatThrownBy(() -> ImagenDeAnimal.exigirQueSePuedaUsar(new CabeceraDeImagen("png", 100, 800), 10))
                .hasMessageContaining("muy chica");
        assertThatThrownBy(() -> ImagenDeAnimal.exigirQueSePuedaUsar(new CabeceraDeImagen("png", 5000, 800), 10))
                .hasMessageContaining("demasiado grande");
        assertThatThrownBy(() -> ImagenDeAnimal.exigirTipoDeContenido("image/jpeg")).isInstanceOf(IllegalArgumentException.class);
        ImagenDeAnimal.exigirTipoDeContenido("image/webp");
    }
}
