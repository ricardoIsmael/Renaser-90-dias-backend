package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.Pareto8020Finder.AccionDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDelEje;
import com.renaser.os.rocks.api.Pareto8020Finder.InsumosDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDelVeintePorCiento;
import com.renaser.os.rocks.api.Pareto8020Finder.Repeticion;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * La cuenta del 80/20 de las acciones del día (spec 031, D-306), pura: recibe las rocas de la persona entre dos fechas, las marcas
 * de «Cierra tu día» y el «hoy» de su zona, y arma las cifras. Sin base de datos, sin reloj y sin Spring, para probarla con cuentas
 * hechas a mano (SC-002).
 *
 * <p><b>Las dos reglas de «el 20 %»</b> (decisión 031-1, A y B): las acciones verdes, y el 20 % de las cumplidas con más impacto
 * (n/5 redondeado hacia arriba, desde cinco cumplidas; con menos, una sola acción explicaría «el 100 %» y eso no es un hallazgo). Los
 * empates se resuelven siempre igual: más impacto, fecha más vieja, posición más baja, id. El puntaje de impacto (1 a 10) lo escribió la
 * persona al planificar. Esta clase no cambia la regla de Pareto ni sus candados, y no da ni quita puntos.
 *
 * <p><b>«Hasta hoy».</b> Lo planificado para un día que no llegó no cuenta (se informa aparte en {@code porVenir}); lo de hoy cuenta,
 * pero una acción de hoy sin cumplir todavía no «se fue»: al día le queda tiempo.
 */
final class ArmadoDel8020 {

    static final int MINIMO_CUMPLIDAS_PARA_EL_VEINTE = 5;
    private static final int MAXIMO_DE_ACCIONES_NOMBRADAS = 5;
    private static final int MAXIMO_DE_NOTAS = 20;

    private static final Comparator<RocaDiaria> POR_IMPACTO = Comparator
            .comparingInt(RocaDiaria::puntajeImpacto).reversed()
            .thenComparing(RocaDiaria::fecha)
            .thenComparingInt(RocaDiaria::posicion)
            .thenComparing(roca -> roca.id().value());

    private ArmadoDel8020() {
    }

    static InsumosDel8020 armar(List<RocaDiaria> rocas, List<MarcaDeCierre> marcas, LocalDate desde, LocalDate hasta,
                                LocalDate hoy) {
        List<RocaDiaria> contadas = rocas.stream().filter(roca -> !roca.fecha().isAfter(hoy)).toList();
        int porVenir = rocas.size() - contadas.size();
        CifrasDel8020 cifras = cifras(contadas, porVenir, marcas, desde, hasta, hoy);
        return new InsumosDel8020(cifras, notasDeCierre(rocas, marcas));
    }

    private static CifrasDel8020 cifras(List<RocaDiaria> contadas, int porVenir, List<MarcaDeCierre> marcas, LocalDate desde,
                                        LocalDate hasta, LocalDate hoy) {
        List<RocaDiaria> cumplidas = contadas.stream().filter(RocaDiaria::completada).sorted(POR_IMPACTO).toList();
        int impactoCumplido = impacto(cumplidas);
        List<RocaDiaria> losQueMasMovieron = elVeintePorCiento(cumplidas);
        List<RocaDiaria> losQueSeFueron = losQueSeFueron(contadas, hoy);
        return new CifrasDel8020(desde, hasta, contadas.size(), cumplidas.size(),
                (int) contadas.stream().map(RocaDiaria::fecha).distinct().count(), porVenir, impacto(contadas), impactoCumplido,
                porcentaje(cumplidas.size(), contadas.size()), verdes(contadas, impactoCumplido),
                reglaDelVeintePorCiento(cumplidas, losQueMasMovieron, impactoCumplido), porEje(contadas, impactoCumplido),
                nombradas(losQueMasMovieron), nombradas(losQueSeFueron),
                cumplioLoQueMenosMovia(losQueSeFueron, cumplidas), cierre(contadas, marcas, hoy));
    }

    private static ReglaDeLasVerdes verdes(List<RocaDiaria> contadas, int impactoCumplido) {
        List<RocaDiaria> verdes = contadas.stream().filter(roca -> roca.color() == ColorPareto.VERDE).toList();
        List<RocaDiaria> verdesCumplidas = verdes.stream().filter(RocaDiaria::completada).toList();
        int impactoDeLasVerdes = impacto(verdesCumplidas);
        return new ReglaDeLasVerdes(verdes.size(), verdesCumplidas.size(), impactoDeLasVerdes,
                porcentaje(impactoDeLasVerdes, impactoCumplido));
    }

    /** {@code cumplidas} ya viene ordenada por impacto. */
    private static List<RocaDiaria> elVeintePorCiento(List<RocaDiaria> cumplidas) {
        if (cumplidas.size() < MINIMO_CUMPLIDAS_PARA_EL_VEINTE) {
            return List.of();
        }
        return cumplidas.subList(0, cuantasSonElVeintePorCiento(cumplidas.size()));
    }

    private static int cuantasSonElVeintePorCiento(int total) {
        return (total + 4) / 5;
    }

    private static ReglaDelVeintePorCiento reglaDelVeintePorCiento(List<RocaDiaria> cumplidas, List<RocaDiaria> elVeinte,
                                                                    int impactoCumplido) {
        if (elVeinte.isEmpty()) {
            return null;
        }
        int impactoDelVeinte = impacto(elVeinte);
        return new ReglaDelVeintePorCiento(elVeinte.size(), impactoDelVeinte, porcentaje(impactoDelVeinte, impactoCumplido));
    }

    /**
     * Lo que más movía y quedó pendiente en un día que ya pasó: las verdes y el 20 % de las planificadas con más impacto. Una acción de
     * hoy sin cumplir todavía no «se fue».
     */
    private static List<RocaDiaria> losQueSeFueron(List<RocaDiaria> contadas, LocalDate hoy) {
        List<RocaDiaria> porImpacto = contadas.stream().sorted(POR_IMPACTO).toList();
        Set<RocaDiaria> elVeinte = porImpacto.size() < MINIMO_CUMPLIDAS_PARA_EL_VEINTE ? Set.of()
                : Set.copyOf(porImpacto.subList(0, cuantasSonElVeintePorCiento(porImpacto.size())));
        return porImpacto.stream()
                .filter(roca -> !roca.completada() && roca.fecha().isBefore(hoy))
                .filter(roca -> roca.color() == ColorPareto.VERDE || elVeinte.contains(roca))
                .toList();
    }

    private static boolean cumplioLoQueMenosMovia(List<RocaDiaria> losQueSeFueron, List<RocaDiaria> cumplidas) {
        if (losQueSeFueron.isEmpty()) {
            return false;
        }
        int impactoMasAltoPendiente = losQueSeFueron.stream().mapToInt(RocaDiaria::puntajeImpacto).max().orElse(0);
        return cumplidas.stream().anyMatch(roca -> roca.puntajeImpacto() < impactoMasAltoPendiente);
    }

    private static List<CifrasDelEje> porEje(List<RocaDiaria> contadas, int impactoCumplido) {
        Map<EjeObjetivo, List<RocaDiaria>> porEje = new EnumMap<>(EjeObjetivo.class);
        contadas.forEach(roca -> porEje.computeIfAbsent(roca.eje(), eje -> new ArrayList<>()).add(roca));
        return porEje.entrySet().stream().map(entrada -> {
            List<RocaDiaria> cumplidas = entrada.getValue().stream().filter(RocaDiaria::completada).toList();
            return new CifrasDelEje(entrada.getKey().name(), entrada.getValue().size(), cumplidas.size(), impacto(cumplidas),
                    porcentaje(impacto(cumplidas), impactoCumplido));
        }).toList();
    }

    private static ResumenDelCierre cierre(List<RocaDiaria> contadas, List<MarcaDeCierre> marcas, LocalDate hoy) {
        Set<LocalDate> cerrados = marcas.stream().map(MarcaDeCierre::fecha).collect(Collectors.toSet());
        long sinCerrar = contadas.stream().map(RocaDiaria::fecha).distinct()
                .filter(fecha -> fecha.isBefore(hoy) && !cerrados.contains(fecha)).count();
        List<RocaDiaria> conRespuesta = contadas.stream().filter(roca -> !roca.completada() && roca.cierre() != null).toList();
        return new ResumenDelCierre(cerrados.size(), (int) sinCerrar,
                repeticiones(conRespuesta, roca -> roca.cierre().respuesta().motivo().name()),
                repeticiones(conRespuesta, roca -> roca.cierre().respuesta().manana().name()));
    }

    private static List<Repeticion> repeticiones(List<RocaDiaria> rocas, java.util.function.Function<RocaDiaria, String> clave) {
        Map<String, Integer> veces = new TreeMap<>();
        rocas.forEach(roca -> veces.merge(clave.apply(roca), 1, Integer::sum));
        return veces.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entrada -> new Repeticion(entrada.getKey(), entrada.getValue())).toList();
    }

    /** Lo que escribió al cerrar: la nota de cada acción pendiente y la del día, de la más vieja a la más nueva; las últimas veinte. */
    private static List<String> notasDeCierre(List<RocaDiaria> rocas, List<MarcaDeCierre> marcas) {
        Map<LocalDate, List<String>> porFecha = new TreeMap<>();
        rocas.stream().filter(roca -> !roca.completada() && roca.cierre() != null && roca.cierre().respuesta().nota() != null)
                .forEach(roca -> porFecha.computeIfAbsent(roca.fecha(), fecha -> new ArrayList<>())
                        .add(roca.cierre().respuesta().nota()));
        marcas.stream().filter(marca -> marca.nota() != null)
                .forEach(marca -> porFecha.computeIfAbsent(marca.fecha(), fecha -> new ArrayList<>()).add(marca.nota()));
        List<String> notas = porFecha.values().stream().flatMap(List::stream).toList();
        return notas.subList(Math.max(0, notas.size() - MAXIMO_DE_NOTAS), notas.size());
    }

    private static List<AccionDel8020> nombradas(List<RocaDiaria> rocas) {
        return rocas.stream().limit(MAXIMO_DE_ACCIONES_NOMBRADAS)
                .map(roca -> new AccionDel8020(roca.eje().name(), roca.color().name(), roca.puntajeImpacto(), roca.fecha(),
                        roca.titulo()))
                .toList();
    }

    private static int impacto(List<RocaDiaria> rocas) {
        return rocas.stream().mapToInt(RocaDiaria::puntajeImpacto).sum();
    }

    /** Entero de 0 a 100, redondeado; {@code null} si no hay nada contra qué medir. */
    static Integer porcentaje(int parte, int total) {
        return total <= 0 ? null : (200 * parte + total) / (2 * total);
    }
}
