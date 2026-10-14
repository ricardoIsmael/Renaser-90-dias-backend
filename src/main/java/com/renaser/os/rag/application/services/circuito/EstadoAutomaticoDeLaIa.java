package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.EstadoDelCircuito;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ReglasDelCircuito;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Lo que {@code plataforma} lee del circuito de la IA (contrato §5 y §6): si una clave {@code ia.*} está apagada
 * automáticamente, la versión que se suma a {@code /funcionalidades/mias} y el estado de cada circuito para la consola.
 * Lo manual manda (FR-017): eso lo resuelve la evaluación de {@code plataforma}, que mira esto después de las filas
 * manuales.
 *
 * <p>{@link #apagadaAutomaticamente} se consulta en cada pedido de SER: lee la copia en memoria, nunca Redis.
 */
@Component
public class EstadoAutomaticoDeLaIa implements EstadoAutomaticoDeFuncionalidades {

    /** El aviso del contrato §6, el que ve la persona en la app. */
    public static final String AVISO = "SER está en pausa por un problema con el proveedor. Vuelve en unos minutos.";

    private static final Logger log = LoggerFactory.getLogger(EstadoAutomaticoDeLaIa.class);

    private final CopiaDelCircuito copia;
    private final EstadoCompartidoDelCircuitoPort estadoCompartido;
    private final ReglasDelCircuito reglas;
    private final Clock clock;

    public EstadoAutomaticoDeLaIa(CopiaDelCircuito copia, EstadoCompartidoDelCircuitoPort estadoCompartido,
                                  ReglasDelCircuito reglas, Clock clock) {
        this.copia = copia;
        this.estadoCompartido = estadoCompartido;
        this.reglas = reglas;
        this.clock = clock;
    }

    @Override
    public Optional<String> apagadaAutomaticamente(String clave) {
        return ParteDeLaIa.deClave(clave).filter(parte -> copia.de(parte).cortaElPaso(clock.now())).map(parte -> AVISO);
    }

    @Override
    public long version() {
        return copia.version();
    }

    /** Para la consola: lee Redis (es un pedido de Soporte TI, no de cada persona); si no responde, la copia. */
    @Override
    public List<CircuitoAutomatico> circuitos() {
        Instant ahora = clock.now();
        return Arrays.stream(ParteDeLaIa.values()).map(parte -> vista(parte, ahora)).toList();
    }

    private CircuitoAutomatico vista(ParteDeLaIa parte, Instant ahora) {
        CircuitoDeIa circuito = copia.de(parte);
        int fallas = 0;
        try {
            circuito = estadoCompartido.leer(parte);
            copia.guardar(circuito);
            fallas = estadoCompartido.fallasEnVentana(parte, ahora, reglas.ventana());
        } catch (RuntimeException e) {
            log.warn("[rag.circuito] la consola ve la copia en memoria de {} ({})", parte.clave(),
                    e.getClass().getSimpleName());
        }
        boolean cerrado = circuito.estado() == EstadoDelCircuito.CERRADO;
        return new CircuitoAutomatico(parte.clave(), circuito.estadoEn(ahora).name(), fallas,
                cerrado ? null : circuito.abiertoDesde(), cerrado ? null : circuito.proximaPrueba());
    }
}
