package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.ports.in.circuito.CircuitoDeLaIa;
import com.renaser.os.rag.application.services.circuito.InterruptoresDeSer;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Las puertas por las que tiene que pasar un análisis antes de llamar a la IA (spec 031, FR-017 y FR-019), las mismas de SER: hay
 * proveedor configurado, Soporte TI no apagó {@code ia.analisis}, el tope de intentos de las últimas 24 horas no se alcanzó y el circuito
 * automático deja pasar. Si alguna está cerrada el análisis queda pendiente y <b>no cuenta como intento</b>: al abrirse se genera, sin
 * duplicar. Apagar esto nunca toca las cifras.
 */
@Component
class PuertasDeLaIa {

    private final GenerarAnalisis8020Port ia;
    private final InterruptoresDeSer interruptores;
    private final CircuitoDeLaIa circuito;
    private final LoadAnalisisDelPeriodoPort analisis;
    private final ReglasDelAnalisis reglas;
    private final Clock clock;

    PuertasDeLaIa(GenerarAnalisis8020Port ia, InterruptoresDeSer interruptores, CircuitoDeLaIa circuito,
                  LoadAnalisisDelPeriodoPort analisis, ReglasDelAnalisis reglas, Clock clock) {
        this.ia = ia;
        this.interruptores = interruptores;
        this.circuito = circuito;
        this.analisis = analisis;
        this.reglas = reglas;
        this.clock = clock;
    }

    /** Lo que se mira sin gastar nada: hay proveedor, el interruptor está prendido y el tope del día no se alcanzó. */
    boolean estanAbiertas() {
        return ia.disponible() && interruptores.analisisPrendido() && !circuito.cortado(ParteDeLaIa.CHAT)
                && analisis.intentosDesde(clock.now().minus(Duration.ofHours(24))) < reglas.generacionesPorDia();
    }

    /** Pide paso al circuito justo antes de llamar: medio abierto, este pedido es la prueba y después hay que contar cómo le fue. */
    boolean pedirPaso() {
        return circuito.dejaPasar(ParteDeLaIa.CHAT);
    }

    void anotarQueRespondio() {
        circuito.anotarExito(ParteDeLaIa.CHAT);
    }

    void anotarQueFallo() {
        circuito.anotarFalla(ParteDeLaIa.CHAT);
    }
}
