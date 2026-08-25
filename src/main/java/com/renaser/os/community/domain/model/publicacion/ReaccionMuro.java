package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Reaccion de un usuario a una publicacion (tabla `reacciones_muro`, PK compuesta
 * publicacion+usuario — P-28 del baseline: a lo sumo una fila por par, ME_GUSTA y
 * NO_ME_GUSTA son mutuamente excluyentes).
 *
 * <p>{@link #calcularToggle} es la regla completa de "tocar para reaccionar, tocar de
 * nuevo para quitar" (wall/service.ts:422-432): pura, sin IO, para poder testearla sin
 * Spring ni Postgres (CLAUDE.MD sec. 5.4.7).
 */
public record ReaccionMuro(PublicacionId publicacionId, UserId usuarioId, TipoReaccion tipo) {

    public ReaccionMuro {
        if (publicacionId == null) {
            throw new IllegalArgumentException("publicacionId es obligatorio");
        }
        if (usuarioId == null) {
            throw new IllegalArgumentException("usuarioId es obligatorio");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo es obligatorio");
        }
    }

    /**
     * Mandar el mismo tipo que ya esta puesto lo saca (un-react); mandar el otro lo
     * reemplaza, sin un borrar-y-crear aparte. Nunca hay mas de una reaccion por usuario.
     */
    public static ResultadoToggle calcularToggle(TipoReaccion existente, TipoReaccion solicitado) {
        if (solicitado == null) {
            throw new IllegalArgumentException("El tipo de reaccion solicitado es obligatorio");
        }
        if (existente == solicitado) {
            return new Quitar();
        }
        return new Reaccionar(solicitado);
    }

    public sealed interface ResultadoToggle permits Reaccionar, Quitar {
    }

    public record Reaccionar(TipoReaccion tipo) implements ResultadoToggle {
    }

    public record Quitar() implements ResultadoToggle {
    }
}
