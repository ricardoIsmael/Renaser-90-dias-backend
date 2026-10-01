package com.renaser.os.rag.infrastructure.adapter.out.metricas;

import com.renaser.os.rag.application.ports.out.metricas.RegistrarMetricaDelAcompanantePort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Las metricas del acompanante en Micrometer (D-237). En Prometheus salen como:
 * <ul>
 *   <li>{@code renaser_acompanante_mensajes_total{agente, resultado}}: ok, error,
 *       proveedor_no_disponible, cancelado o limite_diario.</li>
 *   <li>{@code renaser_acompanante_turnos_seconds_{count,sum,bucket}{agente, resultado}}: cuanto
 *       tardo el turno que llego al modelo, de la pregunta al final del stream.</li>
 *   <li>{@code renaser_acompanante_propuestas_creadas_total{herramienta}} y
 *       {@code renaser_acompanante_propuestas_resueltas_total{herramienta, resolucion}}.</li>
 *   <li>{@code renaser_voz_en_vivo_aperturas_total{resultado}} y
 *       {@code renaser_voz_en_vivo_hablado_seconds_total}.</li>
 * </ul>
 * Las etiquetas son valores de enums o nombres de herramientas del codigo: un conjunto chico y
 * fijo. Nunca una persona.
 */
@Component
class MicrometerMetricasDelAcompananteAdapter implements RegistrarMetricaDelAcompanantePort {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricasDelAcompananteAdapter.class);
    /** Las herramientas son {@code proponer_algo}; si algun dia llegara otra cosa, no abre series. */
    private static final Pattern NOMBRE_DE_HERRAMIENTA = Pattern.compile("[a-z_]{1,60}");

    private final MeterRegistry registro;

    MicrometerMetricasDelAcompananteAdapter(MeterRegistry registro) {
        this.registro = registro;
    }

    @Override
    public void turnoDelChat(AgenteConversacional agente, ResultadoDelTurno resultado, Duration duracion) {
        medir(() -> {
            contarMensaje(agente, etiqueta(resultado));
            Timer.builder("renaser.acompanante.turnos")
                    .description("Turnos del chat con el acompanante que llegaron al modelo")
                    .tag("agente", etiqueta(agente)).tag("resultado", etiqueta(resultado))
                    .register(registro).record(duracion);
        });
    }

    @Override
    public void turnoRechazadoPorLimite(AgenteConversacional agente) {
        medir(() -> contarMensaje(agente, "limite_diario"));
    }

    @Override
    public void propuestaCreada(String herramienta) {
        medir(() -> Counter.builder("renaser.acompanante.propuestas.creadas")
                .description("Propuestas con boton que el acompanante ofrecio")
                .tag("herramienta", herramienta(herramienta))
                .register(registro).increment());
    }

    @Override
    public void propuestaResuelta(String herramienta, ResolucionDePropuesta resolucion) {
        medir(() -> Counter.builder("renaser.acompanante.propuestas.resueltas")
                .description("Propuestas que la persona confirmo o cancelo, y como terminaron")
                .tag("herramienta", herramienta(herramienta)).tag("resolucion", etiqueta(resolucion))
                .register(registro).increment());
    }

    @Override
    public void aperturaDeVozEnVivo(AperturaDeVoz apertura) {
        medir(() -> Counter.builder("renaser.voz.en.vivo.aperturas")
                .description("Intentos de abrir la voz en vivo y como terminaron")
                .tag("resultado", etiqueta(apertura))
                .register(registro).increment());
    }

    @Override
    public void tiempoDeVozEnVivo(Duration tramo) {
        medir(() -> Counter.builder("renaser.voz.en.vivo.hablado")
                .description("Tiempo de voz en vivo cobrado de la cuota")
                .baseUnit("seconds")
                .register(registro).increment(tramo.toMillis() / 1000.0));
    }

    private void contarMensaje(AgenteConversacional agente, String resultado) {
        Counter.builder("renaser.acompanante.mensajes")
                .description("Mensajes al chat del acompanante, por como terminaron")
                .tag("agente", etiqueta(agente)).tag("resultado", resultado)
                .register(registro).increment();
    }

    /** Medir nunca rompe el flujo: una metrica perdida no puede costar un turno. */
    private static void medir(Runnable medicion) {
        try {
            medicion.run();
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar una metrica del acompanante ({})", e.getClass().getSimpleName());
        }
    }

    private static String herramienta(String nombre) {
        return nombre != null && NOMBRE_DE_HERRAMIENTA.matcher(nombre).matches() ? nombre : "otra";
    }

    private static String etiqueta(Enum<?> valor) {
        return valor.name().toLowerCase(Locale.ROOT);
    }
}
