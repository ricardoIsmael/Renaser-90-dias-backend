package com.renaser.os.shared.domain;

import java.time.Duration;
import java.util.List;

/**
 * El contador del límite por cuenta (spec 050, FR-012): cuenta un pedido de un sujeto en un grupo contra una o varias ventanas
 * fijas a la vez, de forma atómica.
 *
 * <p>Reglas que toda implementación cumple:
 * <ul>
 *   <li>mira <b>todas</b> las ventanas antes de contar en ninguna: un pedido que una ventana rechaza no consume las otras;</li>
 *   <li>la ventana es <b>fija</b> y arranca con el primer pedido: el vencimiento se fija solo mientras la clave no tenga uno,
 *       nunca se renueva (si no, «N por minuto» pasaría a ser «N seguidos sin pausa»);</li>
 *   <li>la ventana que rechaza sigue contando (para medir cuánto se pasó), las demás no.</li>
 * </ul>
 * Si no puede contar, lanza una excepción en vez de adivinar: quien decide qué hacer con ella es la política (FR-017).
 */
public interface ContadorDeVentanas {

    Resultado intentar(GrupoDeLimite grupo, SujetoDeLimite sujeto, List<Ventana> ventanas);

    /** Un tope: {@code maximo} pedidos en {@code duracion}; {@code nombre} ({@code 1m}, {@code 1h}, {@code 1d}) va en la clave y en la métrica. */
    record Ventana(String nombre, Duration duracion, int maximo) {

        public Ventana {
            if (maximo <= 0 || duracion.isZero() || duracion.isNegative()) {
                throw new IllegalArgumentException("Una ventana necesita un tope y una duración positivos");
            }
        }
    }

    /** Cuántos pedidos lleva la ventana de {@code ventana}, de su tope. */
    record Uso(String ventana, long usado, int maximo) {

        /** 1,0 es justo el tope; más de 1 es que se pasó. */
        public double proporcion() {
            return (double) usado / maximo;
        }
    }

    /**
     * @param paso            {@code false} si alguna ventana ya estaba en su tope
     * @param ventanaPasada   el nombre de esa ventana; vacío si pasó
     * @param milisParaVencer lo que falta para que venza la ventana que se pasó; 0 si pasó
     * @param usos            lo que se contó: todas las ventanas si pasó; solo la que rechazó si no
     */
    record Resultado(boolean paso, String ventanaPasada, long milisParaVencer, List<Uso> usos) {

        public Resultado {
            usos = List.copyOf(usos);
        }

        /** Lo que va en {@code Retry-After}: segundos enteros, nunca menos de 1. */
        public int segundosParaReintentar() {
            long segundos = (milisParaVencer + 999) / 1000;
            return (int) Math.max(1, Math.min(segundos, Integer.MAX_VALUE));
        }
    }
}
