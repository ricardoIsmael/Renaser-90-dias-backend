package com.renaser.os.community.domain.model.publicacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 049 (D-339): la ruta de la miniatura es la que deja la Lambda; si cambia de un lado y no del otro, el Muro cae a la foto completa. */
class MiniaturaDeFotoTest {

    @Test
    @DisplayName("una foto del Muro tiene su miniatura en muro/miniaturas/<autor>/<id>.webp")
    void fotoDelMuro() {
        assertThat(MiniaturaDeFoto.rutaDe("muro/fotos/u-1/f-2")).contains("muro/miniaturas/u-1/f-2.webp");
    }

    @Test
    @DisplayName("un video, una evidencia de otro módulo o una ruta vacía no tienen miniatura")
    void sinMiniatura() {
        assertThat(MiniaturaDeFoto.rutaDe("muro/videos/u-1/v-2")).isEmpty();
        assertThat(MiniaturaDeFoto.rutaDe("evidencias/r-1/e-2")).isEmpty();
        assertThat(MiniaturaDeFoto.rutaDe("muro/fotos/")).isEmpty();
        assertThat(MiniaturaDeFoto.rutaDe(null)).isEmpty();
    }
}
