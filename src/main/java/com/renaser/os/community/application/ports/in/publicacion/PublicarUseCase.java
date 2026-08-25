package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PublicacionVista;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface PublicarUseCase {

    PublicacionVista publicar(PublicarCommand command);

    /** Sin campo `tipo`: el caso de uso siempre produce MANUAL — HITO_AUTOMATICO y
     * GUERRERO_CAIDO no se crean por esta via (Publicacion.TipoPublicacion, mismo
     * blindaje anti mass-assignment que CLAUDE.MD sec. 5.3.3). `categoriaClave` opcional:
     * un cliente viejo que no la manda sigue publicando igual (wall/schema.ts:51-53). */
    record PublicarCommand(@NotNull UserId autorId, @NotNull String texto, @NotEmpty List<ArchivoEntrada> media,
                            String categoriaClave) {

        public PublicarCommand {
            SelfValidating.validateConstructorArgs(PublicarCommand.class, autorId, texto, media, categoriaClave);
        }
    }

    /** Ya resuelto a bucket+ruta — la compatibilidad con la URL absoluta que manda la app
     * publicada la resuelve el adaptador REST (CM-06, docs/MODULO_COMMUNITY.md sec. 5),
     * el caso de uso nunca ve una URL. */
    record ArchivoEntrada(String bucket, String ruta, String mime) {
    }
}
