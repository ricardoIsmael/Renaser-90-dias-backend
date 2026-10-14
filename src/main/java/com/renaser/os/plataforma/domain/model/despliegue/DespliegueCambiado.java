package com.renaser.os.plataforma.domain.model.despliegue;

import java.time.Instant;
import java.util.Objects;

/**
 * Cambió el despliegue de una funcionalidad (el escalón o sus probadores). Es interno de {@code plataforma}: después del
 * commit, esta instancia recarga su copia de despliegues y avisa a las demás por Redis. El aviso a las personas (push)
 * es otro evento, {@code plataforma.api.FuncionalidadCambiadaEvent}, y solo sale al empezar, cambiar o terminar un
 * despliegue: sumar o quitar una probadora no avisa a nadie (contrato fase 2 §3.4).
 */
public record DespliegueCambiado(String clave, Instant occurredAt) {

    public DespliegueCambiado {
        Objects.requireNonNull(clave, "clave es obligatoria");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }
}
