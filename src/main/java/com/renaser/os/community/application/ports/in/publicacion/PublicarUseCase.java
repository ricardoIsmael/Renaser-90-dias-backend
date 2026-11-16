package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PublicacionVista;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.Formacion;
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
                            String categoriaClave, Formacion formacion, HechoSinRed sinRed) {

        public PublicarCommand {
            SelfValidating.validateConstructorArgs(PublicarCommand.class, autorId, texto, media, categoriaClave, formacion,
                    sinRed);
        }

        public PublicarCommand(UserId autorId, String texto, List<ArchivoEntrada> media, String categoriaClave,
                               Formacion formacion) {
            this(autorId, texto, media, categoriaClave, formacion, null);
        }

        /** Sin elegir formación: la de quien publica (el aprendiz y el personal); Administración tiene que elegirla (spec 024). */
        public PublicarCommand(UserId autorId, String texto, List<ArchivoEntrada> media, String categoriaClave) {
            this(autorId, texto, media, categoriaClave, null);
        }
    }

    /** Ya resuelto a bucket+ruta — la compatibilidad con la URL absoluta que manda la app
     * publicada la resuelve el adaptador REST (CM-06, docs/MODULO_COMMUNITY.md sec. 5),
     * el caso de uso nunca ve una URL. */
    record ArchivoEntrada(String bucket, String ruta, String mime) {
    }

    /**
     * Spec 048, fase 2: la publicacion hecha sin internet. {@code idCliente} identifica el envio (un reintento devuelve la
     * misma publicacion); {@code hechoEn} es la hora real en que se escribio; {@code origenDeLaHora} como en la fase 1
     * ({@code RELOJ_DEL_SISTEMA} o {@code TELEFONO_CORREGIDO}).
     */
    record HechoSinRed(java.time.Instant hechoEn, java.util.UUID idCliente, String origenDeLaHora) {
        public HechoSinRed {
            java.util.Objects.requireNonNull(hechoEn, "hechoEn");
            java.util.Objects.requireNonNull(idCliente, "idCliente");
            origenDeLaHora = "RELOJ_DEL_SISTEMA".equals(origenDeLaHora) ? origenDeLaHora : "TELEFONO_CORREGIDO";
        }
    }
}
