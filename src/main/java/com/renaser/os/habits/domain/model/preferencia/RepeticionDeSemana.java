package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repetir una semana en otra (spec 028, D-296): dar a la semana destino el mismo plan «solo esa semana» que tiene la semana modelo.
 * Pura: recibe las filas de las dos semanas y devuelve QUE escribir y QUE borrar, sin tocar nada.
 *
 * <h2>Reglas</h2>
 * <ul>
 *   <li><b>El resultado es una funcion de la modelo, no una suma.</b> Para cada habito y cada dia: si la fecha de la modelo tiene fila propia,
 *       la fecha equivalente del destino queda con la misma fila; si no la tiene y la del destino si, esa fila se borra (el destino queda como
 *       la modelo). Por eso repetir dos veces deja lo mismo: la segunda vez no hay nada que escribir (idempotente).</li>
 *   <li><b>Solo fechas posteriores a hoy</b>: hoy y lo pasado no se tocan (D-91).</li>
 *   <li><b>Solo hábitos que hoy le corresponden a la persona</b> ({@code aplicables}). Un habito con filas en la modelo que ya no le corresponde
 *       (desactivado, fuera de su audiencia) se deja afuera y se informa, sin fallar el resto.</li>
 *   <li><b>Las pausas no se repiten</b>: viven en otra tabla y este objeto no las conoce.</li>
 * </ul>
 */
public final class RepeticionDeSemana {

    private RepeticionDeSemana() {
    }

    /**
     * Una fecha del destino que cambia. {@code fila == null}: la fecha vuelve al plan de siempre (se borra su fila propia).
     * {@code reemplaza}: la fecha ya tenia un cambio propio distinto, que se pierde.
     */
    public record CambioPlaneado(HabitoId habitoId, LocalDate fecha, FechaPropia fila, boolean reemplaza) {
    }

    /**
     * @param cambios            lo que cambia en el destino, por habito y fecha (ordenado por habito y fecha)
     * @param excluidos          hábitos de la modelo con filas que no se repiten porque ya no le corresponden a la persona
     * @param reemplazaLoPropio  alguna fecha del destino ya tenia un cambio propio distinto
     */
    public record PlanDeRepeticion(List<CambioPlaneado> cambios, Set<HabitoId> excluidos, boolean reemplazaLoPropio) {

        /** Nada que escribir ni borrar: el destino ya es como la modelo. */
        public boolean sinCambios() {
            return cambios.isEmpty();
        }

        public List<FechaPropia> escrituras() {
            return cambios.stream().filter(c -> c.fila() != null).map(CambioPlaneado::fila).toList();
        }

        public List<CambioPlaneado> borrados() {
            return cambios.stream().filter(c -> c.fila() == null).toList();
        }
    }

    /**
     * @param modelo         la semana que se copia
     * @param destino        la semana que queda como la modelo
     * @param filasModelo    todas las filas propias de la persona en la semana modelo (de cualquier habito)
     * @param filasDestino   todas las filas propias de la persona en la semana destino
     * @param aplicables     los hábitos que hoy le corresponden a la persona
     * @param hoy            hoy en su zona
     */
    public static PlanDeRepeticion planear(SemanaCalendaria modelo, SemanaCalendaria destino,
                                           Collection<FechaPropia> filasModelo, Collection<FechaPropia> filasDestino,
                                           Set<HabitoId> aplicables, LocalDate hoy) {
        Map<Clave, FechaPropia> delModelo = indexar(filasModelo);
        Map<Clave, FechaPropia> delDestino = indexar(filasDestino);
        Set<HabitoId> habitos = new LinkedHashSet<>();
        filasModelo.forEach(f -> habitos.add(f.habitoId()));
        filasDestino.forEach(f -> habitos.add(f.habitoId()));
        Set<HabitoId> excluidos = filasModelo.stream().map(FechaPropia::habitoId)
                .filter(h -> !aplicables.contains(h)).collect(Collectors.toCollection(LinkedHashSet::new));

        List<CambioPlaneado> cambios = new ArrayList<>();
        boolean reemplaza = false;
        for (HabitoId habito : habitos) {
            if (!aplicables.contains(habito)) {
                continue;
            }
            for (int i = 0; i < 7; i++) {
                LocalDate origen = modelo.fecha(i);
                LocalDate fecha = destino.fecha(i);
                if (!fecha.isAfter(hoy)) {
                    continue;
                }
                FechaPropia deLaModelo = delModelo.get(new Clave(habito, origen));
                FechaPropia deLaFecha = delDestino.get(new Clave(habito, fecha));
                FechaPropia queda = deLaModelo == null ? null : deLaModelo.enLaFecha(fecha);
                if (java.util.Objects.equals(queda, deLaFecha)) {
                    continue;
                }
                boolean pisa = deLaFecha != null;
                reemplaza |= pisa;
                cambios.add(new CambioPlaneado(habito, fecha, queda, pisa));
            }
        }
        return new PlanDeRepeticion(List.copyOf(cambios), Set.copyOf(excluidos), reemplaza);
    }

    /**
     * Una huella estable de las filas de la modelo que SI se repiten (las de hábitos aplicables): cambia cuando la persona cambia la semana
     * modelo, y es la que la app guarda con «Ahora no» para no volver a ofrecer lo mismo (FR-012).
     */
    public static String huella(Collection<FechaPropia> filasModelo, Set<HabitoId> aplicables) {
        String canonico = filasModelo.stream()
                .filter(f -> aplicables.contains(f.habitoId()))
                .map(RepeticionDeSemana::linea)
                .sorted()
                .collect(Collectors.joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible", e);
        }
    }

    private static String linea(FechaPropia f) {
        return f.habitoId().value() + "|" + f.fecha() + "|" + f.activo() + "|" + f.horaDisparo() + "|" + f.horaLimite() + "|"
                + (f.recordatorio() == null ? "-" : f.recordatorio().activo() + ":" + f.recordatorio().antelaciones()) + "|"
                + (f.alarma() == null ? "-" : f.alarma().toString());
    }

    private static Map<Clave, FechaPropia> indexar(Collection<FechaPropia> filas) {
        Map<Clave, FechaPropia> mapa = new HashMap<>();
        filas.forEach(f -> mapa.put(new Clave(f.habitoId(), f.fecha()), f));
        return mapa;
    }

    private record Clave(HabitoId habitoId, LocalDate fecha) {
    }
}
