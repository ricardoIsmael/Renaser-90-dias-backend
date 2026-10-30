package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.TramosUnidos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Un día de la persona: su ventana, lo que la ocupa y lo que le queda libre (FR-001 a FR-009).
 *
 * <p>Lo ocupado es la unión de todos los tramos (los que se cruzan o se tocan son uno) recortada a la ventana, así que
 * {@code minutosOcupados + minutosLibres} es siempre la ventana entera y ningún hueco se cruza con un ítem (FR-006). Los
 * huecos son lo libre de {@link ReglasDeHuecos#MINUTOS_DEL_HUECO_UTIL} minutos o más; los menores no se listan pero sus
 * minutos sí suman al tiempo libre.
 *
 * <p>Es también lo que se guarda en la foto de la semana, tal cual se calculó: los huecos no se vuelven a derivar al
 * leerla, porque si la regla cambia después, la foto tiene que seguir diciendo lo que se mostró. <b>Lo que la foto no
 * guarda</b> es el {@link DetalleDelDia} (los nombres de lo ocupado y lo cumplido de cada día): un día leído de la foto
 * lo trae en {@code null}.
 *
 * @param ocupados dentro de la ventana, ordenados y sin cruzarse
 * @param huecos   libres de 30 minutos o más, dentro de la ventana y en orden
 * @param conPlan  hubo algo con hora ese día; sin plan, el único hueco es la ventana entera y no es tiempo libre
 *                 «calculado» sino un día en blanco (los huecos que se repiten no lo cuentan)
 * @param detalle  lo que solo existe al calcular el día; {@code null} si el día sale de la foto de una semana cerrada
 */
public record DiaDeHuecos(LocalDate fecha, VentanaDelDia ventana, List<TramoDelDia> ocupados,
                          List<TramoDelDia> huecos, boolean conPlan, DetalleDelDia detalle) {

    public DiaDeHuecos {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(ventana, "ventana es obligatoria");
        ocupados = List.copyOf(ocupados);
        huecos = List.copyOf(huecos);
    }

    /** Un día sin detalle: el que se lee de la foto guardada, que no lleva nombres. */
    public DiaDeHuecos(LocalDate fecha, VentanaDelDia ventana, List<TramoDelDia> ocupados, List<TramoDelDia> huecos,
                       boolean conPlan) {
        this(fecha, ventana, ocupados, huecos, conPlan, null);
    }

    /**
     * @param habitos los que ese día ocupan tiempo; de ellos sale la ventana
     * @param otros   acciones y eventos con hora, ya como ítems
     * @param agenda  las horas ocupadas que la persona guardó con SER para este día de la semana
     */
    public static DiaDeHuecos calcular(LocalDate fecha, List<HabitoConHora> habitos, List<ItemPlanificado> otros,
                                       List<TramoDelDia> agenda) {
        VentanaDelDia ventana = VentanaDelDia.de(habitos);
        List<BloqueOcupado> bloques = bloquesDelDia(fecha, habitos, otros, agenda);
        TramosUnidos unidos = new TramosUnidos(bloques.stream().map(BloqueOcupado::tramo).toList());
        List<TramoDelDia> libresUtiles = unidos.libresEntre(ventana.desde(), ventana.hasta()).stream()
                .filter(libre -> libre.minutos() >= ReglasDeHuecos.MINUTOS_DEL_HUECO_UTIL)
                .toList();
        DetalleDelDia detalle = new DetalleDelDia(dentroDe(ventana, bloques), LoReal.NADA,
                VentanaDelDia.motivoDeDiaEntero(habitos).orElse(null));
        return new DiaDeHuecos(fecha, ventana, unidos.dentroDe(ventana.desde(), ventana.hasta()), libresUtiles,
                !bloques.isEmpty(), detalle);
    }

    /** Este mismo día con lo que se cumplió de lo planificado; solo tiene sentido en un día calculado. */
    public DiaDeHuecos conLoReal(LoReal real) {
        Objects.requireNonNull(detalle, "un dia de la foto no lleva detalle");
        return new DiaDeHuecos(fecha, ventana, ocupados, huecos, conPlan, detalle.conLoReal(real));
    }

    public int minutosOcupados() {
        return ocupados.stream().mapToInt(TramoDelDia::minutos).sum();
    }

    public int minutosLibres() {
        return ventana.minutos() - minutosOcupados();
    }

    /** Todo lo que ocupa ese día, sin unir: la agenda personal, los hábitos, las acciones y los eventos. */
    private static List<BloqueOcupado> bloquesDelDia(LocalDate fecha, List<HabitoConHora> habitos,
                                                      List<ItemPlanificado> otros, List<TramoDelDia> agenda) {
        List<BloqueOcupado> bloques = new ArrayList<>();
        agenda.forEach(tramo -> bloques.add(BloqueOcupado.deAgenda(tramo)));
        habitos.forEach(habito -> habito.enElDia(fecha).bloqueEn(fecha).ifPresent(bloques::add));
        otros.forEach(item -> item.bloqueEn(fecha).ifPresent(bloques::add));
        return bloques;
    }

    /** Lo que cae dentro de la ventana, recortado a ella y en orden (Dormir, que empieza donde termina, queda fuera). */
    private static List<BloqueOcupado> dentroDe(VentanaDelDia ventana, List<BloqueOcupado> bloques) {
        return bloques.stream()
                .flatMap(bloque -> bloque.recortadoA(ventana.desde(), ventana.hasta()).stream())
                .sorted(BloqueOcupado.EN_ORDEN)
                .toList();
    }
}
