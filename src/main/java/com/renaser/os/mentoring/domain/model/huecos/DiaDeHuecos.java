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
 * leerla, porque si la regla cambia después, la foto tiene que seguir diciendo lo que se mostró.
 *
 * @param ocupados dentro de la ventana, ordenados y sin cruzarse
 * @param huecos   libres de 30 minutos o más, dentro de la ventana y en orden
 * @param conPlan  hubo algo con hora ese día; sin plan, el único hueco es la ventana entera y no es tiempo libre
 *                 «calculado» sino un día en blanco (los huecos que se repiten no lo cuentan)
 */
public record DiaDeHuecos(LocalDate fecha, VentanaDelDia ventana, List<TramoDelDia> ocupados,
                          List<TramoDelDia> huecos, boolean conPlan) {

    public DiaDeHuecos {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(ventana, "ventana es obligatoria");
        ocupados = List.copyOf(ocupados);
        huecos = List.copyOf(huecos);
    }

    /**
     * @param habitos los que ese día ocupan tiempo; de ellos sale la ventana
     * @param otros   acciones y eventos con hora, ya como ítems
     * @param agenda  las horas ocupadas que la persona guardó con SER para este día de la semana
     */
    public static DiaDeHuecos calcular(LocalDate fecha, List<HabitoConHora> habitos, List<ItemPlanificado> otros,
                                       List<TramoDelDia> agenda) {
        VentanaDelDia ventana = VentanaDelDia.de(habitos);
        List<TramoDelDia> sueltos = new ArrayList<>(agenda);
        habitos.forEach(habito -> habito.enElDia(fecha).tramoEn(fecha).ifPresent(sueltos::add));
        otros.forEach(item -> item.tramoEn(fecha).ifPresent(sueltos::add));
        TramosUnidos unidos = new TramosUnidos(sueltos);
        List<TramoDelDia> libresUtiles = unidos.libresEntre(ventana.desde(), ventana.hasta()).stream()
                .filter(libre -> libre.minutos() >= ReglasDeHuecos.MINUTOS_DEL_HUECO_UTIL)
                .toList();
        return new DiaDeHuecos(fecha, ventana, unidos.dentroDe(ventana.desde(), ventana.hasta()), libresUtiles,
                !sueltos.isEmpty());
    }

    public int minutosOcupados() {
        return ocupados.stream().mapToInt(TramoDelDia::minutos).sum();
    }

    public int minutosLibres() {
        return ventana.minutos() - minutosOcupados();
    }
}
