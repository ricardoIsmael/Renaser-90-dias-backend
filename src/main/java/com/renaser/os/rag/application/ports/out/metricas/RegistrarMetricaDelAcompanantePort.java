package com.renaser.os.rag.application.ports.out.metricas;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;

import java.time.Duration;

/**
 * Cuanto se usa el acompanante, para los paneles de Grafana (D-237). Solo cuentas y tiempos: nunca
 * quien, nunca que dijo. Ningun metodo recibe un {@code UserId} a proposito: una etiqueta por
 * persona multiplicaria las series (y las pondria en manos de quien vea el panel).
 *
 * <p>El adaptador ({@code MicrometerMetricasDelAcompananteAdapter}) no puede romper el flujo: si
 * registrar falla, se pierde la medicion, no el turno.
 */
public interface RegistrarMetricaDelAcompanantePort {

    /** Un mensaje al chat de SER (o del tutor) que llego al modelo y termino como dice {@code resultado}. */
    void turnoDelChat(AgenteConversacional agente, ResultadoDelTurno resultado, Duration duracion);

    /** Un mensaje rechazado antes de llegar al modelo porque ya uso los de hoy. */
    void turnoRechazadoPorLimite(AgenteConversacional agente);

    /** Una propuesta nueva con boton (no cuenta la que se reusa por ser igual a una pendiente). */
    void propuestaCreada(String herramienta);

    /** Como termino una propuesta que la persona toco. */
    void propuestaResuelta(String herramienta, ResolucionDePropuesta resolucion);

    /** Un intento de abrir la voz en vivo y como termino. */
    void aperturaDeVozEnVivo(AperturaDeVoz apertura);

    /** Segundos hablados que se cobraron de la cuota de voz en vivo. */
    void tiempoDeVozEnVivo(Duration tramo);

    enum ResultadoDelTurno {
        OK,
        /** El proveedor dijo que no (cuota de Google, saturado): {@code ProveedorIaNoDisponibleException}. */
        PROVEEDOR_NO_DISPONIBLE,
        ERROR,
        /** La app corto el stream antes del final (cerro la pantalla, perdio la red). */
        CANCELADO
    }

    enum ResolucionDePropuesta { CONFIRMADA, FALLIDA, CANCELADA }

    enum AperturaDeVoz { ABIERTA, CUOTA_AGOTADA, NO_DISPONIBLE, NO_AUTORIZADA }
}
