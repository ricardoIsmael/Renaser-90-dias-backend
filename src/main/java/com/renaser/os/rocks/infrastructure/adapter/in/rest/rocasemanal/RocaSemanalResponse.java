package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El objetivo de una semana, tal como lo ve la app.
 *
 * <h2>Por que {@code accionesCriticas} sigue aca si la tabla ya no existe</h2>
 *
 * Porque sacarla del JSON <b>rompe a los usuarios que no actualizaron la app</b>, y esta app no
 * tiene actualizacion por aire: no usa {@code expo-updates}, asi que un build instalado se queda
 * con su bundle hasta que la persona instala uno nuevo. En todos los builds anteriores al
 * 2026-09-22 el esquema decia {@code accionesCriticas: z.array(z.string())} — <b>obligatorio</b>,
 * no opcional: si el servidor deja de mandarlo, a esa gente le falla el parseo y se queda sin ver
 * su plan semanal.
 *
 * <p>Por eso viaja siempre {@code []}: es lo que ya llegaba desde la V61 —la tabla estaba vacia— y
 * lo que el esquema viejo acepta sin chistar. No es un dato, es compatibilidad.
 *
 * <p><b>Cuando se puede borrar de verdad:</b> cuando el padron este corriendo un build igual o
 * posterior al commit {@code 698a276}, donde el campo pasó a {@code .optional()}. Ahi se saca este
 * campo, esta clase queda sin {@code List} y no hay nada mas que tocar: en la base ya no existe
 * (V62) ni en el dominio.
 */
public record RocaSemanalResponse(UUID id, UUID rocaMaestraId, int numeroSemana, String titulo,
                                   List<String> accionesCriticas, String obstaculo, String contingencia,
                                   Integer autoevaluacionInicio, Integer autoevaluacionFin, String bloqueoPrincipal,
                                   String correccion, Instant creadoEn, Instant actualizadoEn) {

    public static RocaSemanalResponse from(RocaSemanal r) {
        return new RocaSemanalResponse(r.id().value(), r.rocaMaestraId().value(), r.numeroSemana(), r.titulo(),
                List.of(), r.obstaculo(), r.contingencia(), r.autoevaluacionInicio(), r.autoevaluacionFin(),
                r.bloqueoPrincipal(), r.correccion(), r.creadoEn(), r.actualizadoEn());
    }
}
