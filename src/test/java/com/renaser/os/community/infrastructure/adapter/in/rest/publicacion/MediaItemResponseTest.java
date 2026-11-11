package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 049: la foto del feed lleva la URL de su miniatura (o null), y la app cae a la completa si la miniatura no existe. */
class MediaItemResponseTest {

    @Test
    @DisplayName("con miniatura viaja thumbnailUrl; sin ella, null (video, evidencia, servidor anterior)")
    void thumbnailUrl() { // spec 049 FR-001
        var con = MediaItemResponse.from(new MediaFirmada(URI.create("https://s3/muro/fotos/u/f?x=1"), "image/jpeg", 0,
                URI.create("https://s3/muro/miniaturas/u/f.webp?x=2")));
        assertThat(con.thumbnailUrl()).isEqualTo("https://s3/muro/miniaturas/u/f.webp?x=2");
        assertThat(MediaItemResponse.from(new MediaFirmada(URI.create("https://s3/muro/videos/u/v"), "video/mp4", 0)).thumbnailUrl()).isNull();
    }
}
