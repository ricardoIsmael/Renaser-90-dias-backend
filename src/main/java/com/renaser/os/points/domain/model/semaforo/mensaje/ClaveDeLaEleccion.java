package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * La identidad de una elección guardada (D-277, D-281): una fila por persona, día LOCAL, canal y grupo. Es la clave
 * primaria de {@code mensajes_semaforo_elegidos}; reunirla en un valor evita pasar cuatro datos sueltos a cada
 * operación del puerto.
 *
 * @param fecha el día en la zona de la persona, nunca el del servidor (regla 02)
 * @param grupo el color del día o el caso en que está, al momento de elegir
 */
public record ClaveDeLaEleccion(UserId persona, LocalDate fecha, GrupoDelMensaje grupo, CanalDelMensaje canal) {

    public ClaveDeLaEleccion {
        Objects.requireNonNull(persona, "persona es obligatoria");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(grupo, "grupo es obligatorio");
        Objects.requireNonNull(canal, "canal es obligatorio");
    }
}
