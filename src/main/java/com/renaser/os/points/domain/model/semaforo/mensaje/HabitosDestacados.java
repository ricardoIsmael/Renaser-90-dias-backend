package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.CumplimientoDeHabito;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * El hábito que más le cuesta a la persona y el que mejor le va en la ventana del semáforo (FR-006), para los
 * mensajes que los nombran.
 *
 * <ul>
 *   <li>Solo cuentan los hábitos con al menos 2 días programados: con uno solo, una tasa de 0 % o 100 % es ruido.</li>
 *   <li>El fuerte además necesita una tasa de 50 % o más: no se felicita a nadie por su «mejor» hábito si falló
 *       la mayoría.</li>
 *   <li>No se adivina: si hay empate en el extremo, ese dato queda vacío; y si el flojo y el fuerte serían el mismo
 *       hábito (hay uno solo que cuente), los dos quedan vacíos.</li>
 * </ul>
 * Las tasas se comparan por producto cruzado (sin decimales), así 2 de 3 y 4 de 6 son un empate exacto.
 *
 * @param flojo  nombre del hábito más flojo, o {@code null}
 * @param fuerte nombre del hábito más fuerte, o {@code null}
 */
public record HabitosDestacados(String flojo, String fuerte) {

    static final int DIAS_MINIMOS = 2;
    /** Tasa mínima del fuerte, en porcentaje. */
    static final int TASA_MINIMA_DEL_FUERTE = 50;

    public static HabitosDestacados de(List<CumplimientoDeHabito> habitos) {
        List<CumplimientoDeHabito> validos = habitos.stream().filter(h -> h.programados() >= DIAS_MINIMOS).toList();
        if (validos.size() < 2) {
            return new HabitosDestacados(null, null);
        }
        return new HabitosDestacados(unico(validos, PorTasa.ASCENDENTE, h -> true),
                unico(validos, PorTasa.DESCENDENTE, HabitosDestacados::alcanzaParaSerFuerte));
    }

    private static boolean alcanzaParaSerFuerte(CumplimientoDeHabito habito) {
        return habito.cumplidos() * 100 >= TASA_MINIMA_DEL_FUERTE * habito.programados();
    }

    /** El nombre del hábito de un extremo si es único y cumple la condición; {@code null} si hay empate. */
    private static String unico(List<CumplimientoDeHabito> habitos, Comparator<CumplimientoDeHabito> orden,
                                Predicate<CumplimientoDeHabito> condicion) {
        CumplimientoDeHabito extremo = habitos.stream().min(orden).orElseThrow();
        long empatados = habitos.stream().filter(h -> orden.compare(h, extremo) == 0).count();
        return empatados == 1 && condicion.test(extremo) ? extremo.nombre() : null;
    }

    /** Orden por tasa de cumplimiento, comparando {@code a/b} con {@code c/d} como {@code a*d} contra {@code c*b}. */
    private static final class PorTasa {

        static final Comparator<CumplimientoDeHabito> ASCENDENTE = (a, b) ->
                Integer.compare(a.cumplidos() * b.programados(), b.cumplidos() * a.programados());
        static final Comparator<CumplimientoDeHabito> DESCENDENTE = ASCENDENTE.reversed();

        private PorTasa() {
        }
    }
}
