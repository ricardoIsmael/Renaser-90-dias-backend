package com.renaser.os.plataforma.domain.model.despliegue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las últimas 24 h de una funcionalidad, hora por hora, dentro y fuera del despliegue (contrato fase 2 §3.4; decisión
 * del dueño P5). Siempre 24 horas, de la más vieja a la actual, y las que no tienen datos en 0: el gráfico de la app no
 * tiene que rellenar huecos ni adivinar si una hora sin fila es «nadie la usó» o «no hay datos».
 */
public final class MetricasPorHora {

    public static final int HORAS = 24;

    private MetricasPorHora() {
    }

    /** La primera hora de la ventana que termina en la hora de {@code ahora} (incluida). */
    public static Instant desde(Instant ahora) {
        return ConteoDeDespliegue.horaDe(ahora).minus(Duration.ofHours(HORAS - 1L));
    }

    /** @param filas las de una funcionalidad; se ignoran las que caen fuera de la ventana */
    public static List<MetricaDeLaHora> ultimas24Horas(Instant ahora, Collection<ConteoDeDespliegue> filas) {
        Map<Instant, Conteo> dentro = new HashMap<>();
        Map<Instant, Conteo> fuera = new HashMap<>();
        filas.forEach(fila -> (fila.dentro() ? dentro : fuera)
                .merge(fila.hora(), new Conteo(fila.pedidos(), fila.errores()), Conteo::mas));
        List<MetricaDeLaHora> serie = new ArrayList<>(HORAS);
        for (Instant hora = desde(ahora); serie.size() < HORAS; hora = hora.plus(Duration.ofHours(1))) {
            serie.add(new MetricaDeLaHora(hora, dentro.getOrDefault(hora, Conteo.CERO),
                    fuera.getOrDefault(hora, Conteo.CERO)));
        }
        return List.copyOf(serie);
    }

    /** Pedidos y errores 5xx de un grupo (dentro o fuera) en una hora. */
    public record Conteo(long pedidos, long errores) {

        public static final Conteo CERO = new Conteo(0, 0);

        public Conteo mas(Conteo otro) {
            return new Conteo(pedidos + otro.pedidos, errores + otro.errores);
        }
    }

    /** Una hora del gráfico: quienes estaban dentro del despliegue y quienes no. */
    public record MetricaDeLaHora(Instant hora, Conteo dentro, Conteo fuera) {
    }
}
