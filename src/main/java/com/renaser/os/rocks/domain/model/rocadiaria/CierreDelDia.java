package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La regla de cerrar el día (spec 017): una respuesta por cada acción que siga pendiente, ninguna por una que no es del día, y
 * nunca completar nada.
 *
 * <p><b>Cerrar no cumple acciones.</b> Esta clase solo llama {@link RocaDiaria#cerrarConRespuesta} y
 * {@link RocaDiaria#quitarCierre}, que no tocan {@code completada} ni los puntos; no recibe un {@code Clock} para pagar nada
 * ni publica eventos. El test del dominio lo fija.
 */
public final class CierreDelDia {

    private CierreDelDia() {
    }

    /** La respuesta de una acción, tal como llega: el id que dijo la persona y lo que contestó. */
    public record RespuestaDeUnaAccion(RocaDiariaId accionId, RespuestaDeCierre respuesta) {
    }

    /**
     * Solo se cierra el día de hoy. Una fecha que todavía no llega es un pedido mal hecho (400); una que ya pasó, un día que ya
     * cerró (409).
     */
    public static void exigirQueSeaHoy(LocalDate fecha, LocalDate hoy) {
        if (fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("INVALID_DATE: ese día todavía no llega");
        }
        if (fecha.isBefore(hoy)) {
            throw new CierreDeOtroDiaException();
        }
    }

    /**
     * Aplica las respuestas a las rocas del día, en memoria. <b>Todo o nada</b>: primero se comprueba que ninguna pendiente quede sin
     * respuesta y recién después se cambia algo, así un 400 no deja rocas a medio cerrar.
     *
     * <p>Una acción pendiente SIN respuesta es 400; una respuesta de una acción cumplida se ignora (ya no hay nada que explicar) y
     * limpia la que tuviera; una respuesta de una acción que no es de este día, o repetida, es 400.
     *
     * @return las rocas cuyo cierre cambió (las que hay que guardar)
     */
    public static List<RocaDiaria> cerrar(List<RocaDiaria> rocasDelDia, List<RespuestaDeUnaAccion> respuestas, Instant ahora) {
        Map<RocaDiariaId, RespuestaDeCierre> porAccion = indexar(rocasDelDia, respuestas);
        exigirUnaRespuestaPorPendiente(rocasDelDia, porAccion);
        List<RocaDiaria> cambiadas = new ArrayList<>();
        for (RocaDiaria roca : rocasDelDia) {
            if (aplicarA(roca, porAccion.get(roca.id()), ahora)) {
                cambiadas.add(roca);
            }
        }
        return cambiadas;
    }

    private static void exigirUnaRespuestaPorPendiente(List<RocaDiaria> rocasDelDia,
                                                       Map<RocaDiariaId, RespuestaDeCierre> porAccion) {
        rocasDelDia.stream()
                .filter(roca -> !roca.completada() && !porAccion.containsKey(roca.id()))
                .findFirst()
                .ifPresent(roca -> {
                    throw new IllegalArgumentException("Cuenta qué pasó con «" + roca.titulo() + "» y qué harás mañana");
                });
    }

    /** @return si el cierre de la roca cambió */
    private static boolean aplicarA(RocaDiaria roca, RespuestaDeCierre respuesta, Instant ahora) {
        if (roca.completada()) {
            boolean teniaRespuesta = roca.cierre() != null;
            roca.quitarCierre();
            return teniaRespuesta;
        }
        roca.cerrarConRespuesta(respuesta, ahora);
        return true;
    }

    private static Map<RocaDiariaId, RespuestaDeCierre> indexar(List<RocaDiaria> rocasDelDia,
                                                               List<RespuestaDeUnaAccion> respuestas) {
        List<RocaDiariaId> delDia = rocasDelDia.stream().map(RocaDiaria::id).toList();
        Map<RocaDiariaId, RespuestaDeCierre> porAccion = new HashMap<>();
        for (RespuestaDeUnaAccion dicha : respuestas) {
            if (!delDia.contains(dicha.accionId())) {
                throw new IllegalArgumentException("Esa acción no es de este día");
            }
            if (porAccion.put(dicha.accionId(), dicha.respuesta()) != null) {
                throw new IllegalArgumentException("Esa acción viene repetida");
            }
        }
        return porAccion;
    }
}
