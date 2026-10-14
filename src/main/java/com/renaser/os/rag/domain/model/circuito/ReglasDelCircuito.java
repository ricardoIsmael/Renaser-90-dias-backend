package com.renaser.os.rag.domain.model.circuito;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Los números del circuito de la IA (spec 002, FR-016; plan.md D7). Los de {@link #POR_DEFECTO} son los de la spec,
 * todavía «a confirmar» por el dueño: por eso se pueden cambiar por propiedad sin tocar código
 * ({@code renaser.ia.circuito.*}).
 *
 * @param fallasParaAbrir cuántas fallas del proveedor dentro de {@code ventana} abren el circuito (5)
 * @param ventana         la ventana deslizante en la que se cuentan (2 min)
 * @param esperas         cuánto espera el circuito abierto antes de dejar pasar la prueba, según cuántas veces se
 *                        abrió seguido: la primera 2 min, la segunda 5, de la tercera en adelante 15
 * @param plazoDeLaPrueba cuánto se le reserva al pedido de prueba antes de dejar que otro lo intente, por si se
 *                        cortó sin avisar cómo le fue (2 min: el tope de un pedido asíncrono de MVC)
 */
public record ReglasDelCircuito(int fallasParaAbrir, Duration ventana, List<Duration> esperas,
                                Duration plazoDeLaPrueba) {

    public static final ReglasDelCircuito POR_DEFECTO = new ReglasDelCircuito(5, Duration.ofMinutes(2),
            List.of(Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(15)), Duration.ofMinutes(2));

    public ReglasDelCircuito {
        if (fallasParaAbrir < 1) {
            throw new IllegalArgumentException("fallasParaAbrir tiene que ser al menos 1");
        }
        requirePositiva(ventana, "ventana");
        requirePositiva(plazoDeLaPrueba, "plazoDeLaPrueba");
        esperas = List.copyOf(Objects.requireNonNull(esperas, "esperas es obligatorio"));
        if (esperas.isEmpty()) {
            throw new IllegalArgumentException("hace falta al menos una espera");
        }
        esperas.forEach(espera -> requirePositiva(espera, "espera"));
    }

    /** La espera tras la apertura número {@code n} de una racha (1 = la primera); la última se repite. */
    public Duration esperaTrasLaApertura(int n) {
        return esperas.get(Math.min(Math.max(n, 1), esperas.size()) - 1);
    }

    private static void requirePositiva(Duration duracion, String nombre) {
        Objects.requireNonNull(duracion, nombre + " es obligatorio");
        if (duracion.isNegative() || duracion.isZero()) {
            throw new IllegalArgumentException(nombre + " tiene que ser positiva");
        }
    }
}
