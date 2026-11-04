package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.SalaDeReunionPort;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.CodigoDeSalaMeet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Un Google de mentira para las pruebas (spec 040): se comporta como el verdadero en lo que importa —el evento con el mismo id NO se duplica, la
 * sala tiene un enlace estable, borrar dos veces no falla— y se le puede pedir que falle, que tarde en terminar la sala o que no deje cambiar quién
 * puede entrar. Sin red y sin ninguna credencial.
 */
final class GoogleSimulado implements SalaDeReunionPort {

    /** idGoogle -> enlace de la sala. */
    final Map<String, String> eventos = new LinkedHashMap<>();
    final Map<String, PedidoDeSala> ultimoPedido = new LinkedHashMap<>();
    final Map<String, AccesoSalaMeet> accesoPorEnlace = new LinkedHashMap<>();
    final List<String> llamadas = new ArrayList<>();

    /** Si no es nulo, la PRÓXIMA llamada lanza esto (y se olvida). */
    RuntimeException fallaProxima;
    /** Si no es nulo, TODAS las llamadas lanzan esto. */
    RuntimeException fallaSiempre;
    /** La primera vez que se pide una sala, Google contesta {@code pending}; la segunda, ya está. */
    boolean tardaEnCrearLaSala;
    /** Google no deja cambiar quién puede entrar: la sala conserva el acceso de siempre. */
    boolean noDejaCambiarElAcceso;
    /** Google crea el evento pero la respuesta se pierde en el camino: Renaser no se entera (SC-011). */
    boolean perderLaRespuestaAlCrear;
    /** Se ejecuta en cada llamada, antes de contestar: para comprobar qué había pasado ya (por ejemplo, que el evento estaba guardado). */
    Runnable alLlamar = () -> { };
    private int numeroDeSala;
    private final Map<String, Boolean> yaRespondioPendiente = new LinkedHashMap<>();

    @Override
    public ResultadoDeSala crear(PedidoDeSala pedido) {
        entrar("crear:" + pedido.idGoogle());
        ultimoPedido.put(pedido.idGoogle(), pedido);
        String enlace = eventos.computeIfAbsent(pedido.idGoogle(), id -> nuevoEnlace());
        if (perderLaRespuestaAlCrear) {
            perderLaRespuestaAlCrear = false;
            throw new com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException("Se perdió la respuesta", 0);
        }
        if (tardaEnCrearLaSala && yaRespondioPendiente.putIfAbsent(pedido.idGoogle(), Boolean.TRUE) == null) {
            return ResultadoDeSala.pendiente();
        }
        return new ResultadoDeSala(enlace);
    }

    @Override
    public void actualizar(PedidoDeSala pedido) {
        entrar("actualizar:" + pedido.idGoogle());
        if (!eventos.containsKey(pedido.idGoogle())) {
            throw new com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException("No existe", 404);
        }
        ultimoPedido.put(pedido.idGoogle(), pedido);
    }

    @Override
    public void borrar(String idGoogle) {
        entrar("borrar:" + idGoogle);
        eventos.remove(idGoogle);
    }

    @Override
    public Optional<AccesoSalaMeet> fijarAcceso(String enlace, AccesoSalaMeet acceso) {
        entrar("acceso:" + CodigoDeSalaMeet.deEnlace(enlace).orElse("?") + ":" + acceso);
        if (noDejaCambiarElAcceso) {
            return Optional.of(accesoPorEnlace.getOrDefault(enlace, AccesoSalaMeet.CON_PERMISO));
        }
        accesoPorEnlace.put(enlace, acceso);
        return Optional.of(acceso);
    }

    long llamadasDe(String prefijo) {
        return llamadas.stream().filter(l -> l.startsWith(prefijo)).count();
    }

    /** Cuántas salas distintas existen de verdad en Google. */
    int salasCreadas() {
        return eventos.size();
    }

    private void entrar(String llamada) {
        llamadas.add(llamada);
        alLlamar.run();
        if (fallaSiempre != null) {
            throw fallaSiempre;
        }
        if (fallaProxima != null) {
            RuntimeException falla = fallaProxima;
            fallaProxima = null;
            throw falla;
        }
    }

    private String nuevoEnlace() {
        int n = numeroDeSala++;
        String sufijo = "" + (char) ('a' + n / 26 % 26) + (char) ('a' + n % 26);
        return "https://meet.google.com/aaa-aaaa-a" + sufijo;
    }
}
