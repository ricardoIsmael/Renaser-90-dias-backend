package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.rocks.domain.model.cierre.MomentoDelCierre;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La regla de cerrar el día (spec 017): una respuesta por cada acción que siga pendiente <b>y cuya hora ya llegó</b> (las de hora
 * futura no se exigen: decisión del dueño, 2026-10-30), ninguna por una que no es del día, y nunca completar nada.
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
     * ¿Se le exige a la persona una respuesta de esta acción al cerrar? Sí si sigue pendiente y su hora de inicio ya llegó (o no tiene
     * hora). Una pendiente de hora futura no se pregunta ni se exige; una cumplida, tampoco. La usa también la lectura del cierre para
     * decirle a la app por cuáles preguntar.
     */
    public static boolean seExigeRespuesta(RocaDiaria roca, MomentoDelCierre momento) {
        return !roca.completada() && momento.llegoLaHora(roca.horaInicio());
    }

    /**
     * Aplica las respuestas a las rocas del día, en memoria. <b>Todo o nada</b>: primero se comprueba que ninguna pendiente exigible
     * quede sin respuesta y recién después se cambia algo, así un 400 no deja rocas a medio cerrar.
     *
     * <p>Una acción pendiente cuya hora ya llegó (o sin hora) y SIN respuesta es 400. Una pendiente de hora futura no se exige: si
     * viene con respuesta se guarda, y si no viene queda sin respuesta (guardar reemplaza lo del día). Una respuesta de una acción
     * cumplida se ignora (ya no hay nada que explicar) y limpia la que tuviera; una respuesta de una acción que no es de este día, o
     * repetida, es 400.
     *
     * @return las rocas cuyo cierre cambió (las que hay que guardar)
     */
    public static List<RocaDiaria> cerrar(List<RocaDiaria> rocasDelDia, List<RespuestaDeUnaAccion> respuestas,
                                          MomentoDelCierre momento) {
        Map<RocaDiariaId, RespuestaDeCierre> porAccion = indexar(rocasDelDia, respuestas);
        exigirUnaRespuestaPorPendiente(rocasDelDia, porAccion, momento);
        List<RocaDiaria> cambiadas = new ArrayList<>();
        for (RocaDiaria roca : rocasDelDia) {
            if (aplicarA(roca, porAccion.get(roca.id()), momento)) {
                cambiadas.add(roca);
            }
        }
        return cambiadas;
    }

    private static void exigirUnaRespuestaPorPendiente(List<RocaDiaria> rocasDelDia,
                                                       Map<RocaDiariaId, RespuestaDeCierre> porAccion,
                                                       MomentoDelCierre momento) {
        rocasDelDia.stream()
                .filter(roca -> seExigeRespuesta(roca, momento) && !porAccion.containsKey(roca.id()))
                .findFirst()
                .ifPresent(roca -> {
                    throw new IllegalArgumentException("Cuenta qué pasó con «" + roca.titulo() + "» y qué harás mañana");
                });
    }

    /** @return si el cierre de la roca cambió */
    private static boolean aplicarA(RocaDiaria roca, RespuestaDeCierre respuesta, MomentoDelCierre momento) {
        if (roca.completada() || respuesta == null) {
            // Cumplida (ya no hay nada que explicar) o pendiente de hora futura sin respuesta: queda sin respuesta de cierre.
            boolean teniaRespuesta = roca.cierre() != null;
            roca.quitarCierre();
            return teniaRespuesta;
        }
        roca.cerrarConRespuesta(respuesta, momento.instante());
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
