package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Contrato publico de `community` para que OTRO modulo cree una publicacion real en el
 * Muro (tabla `publicaciones_muro`) a partir de una evidencia que ya subio por su
 * cuenta — sin este puerto, un modulo como `rocks` no tenia forma de hacer que
 * {@code publishedToWall=true} tuviera efecto real (Hueco #17, docs/MODULO_ROCKS.md
 * sec. 11.2, "Camino B").
 *
 * <p>Reutiliza el mismo caso de uso interno que {@code POST /api/v1/wall} (via
 * {@code Publicacion.publicarAutomatica}, no una tabla ni una regla nueva) — la unica
 * diferencia es que el {@code tipo} resultante es {@code HITO_AUTOMATICO}, nunca
 * {@code MANUAL} (reservado a que un actor publique de su puno y letra) ni
 * {@code GUERRERO_CAIDO}. El autor SIGUE pasando por las mismas reglas de autorizacion
 * que un POST normal (cuenta activa, rol habilitado a publicar) — un modulo que llama
 * esto con un {@code autorId} suspendido o sin permiso recibe la misma
 * {@code NotAuthorizedException} que recibiria via REST.
 */
public interface PublicarEnMuroPort {

    /** Devuelve el id de la publicacion creada. */
    UUID publicarDesdeEvidencia(PublicarDesdeEvidenciaComando comando);

    /**
     * {@code bucket}/{@code ruta} ya resueltos por el {@code AlmacenamientoPort} del
     * modulo llamador (evidencia ya subida, no se sube nada aca). {@code mime} debe
     * empezar con {@code image/} o {@code video/} (mismo CHECK que
     * {@code MediaPublicacion} — el Muro es un feed visual, nunca acepta audio/texto
     * puro). {@code texto} es obligatorio: el Muro no admite una publicacion sin
     * leyenda, ni siquiera generada automaticamente.
     */
    record PublicarDesdeEvidenciaComando(UserId autorId, String texto, String bucket, String ruta, String mime) {

        public PublicarDesdeEvidenciaComando {
            if (autorId == null) {
                throw new IllegalArgumentException("autorId es obligatorio");
            }
            if (texto == null || texto.isBlank()) {
                throw new IllegalArgumentException("texto es obligatorio");
            }
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("bucket es obligatorio");
            }
            if (ruta == null || ruta.isBlank()) {
                throw new IllegalArgumentException("ruta es obligatoria");
            }
            if (mime == null || mime.isBlank()) {
                throw new IllegalArgumentException("mime es obligatorio");
            }
        }
    }
}
