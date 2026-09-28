package com.renaser.os.onboarding.domain.model.caja;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Un envío que salió, como lo sigue el aprendiz en «Tu Caja Renaser»: por dónde fue, con qué código, en qué
 * terminó y cuándo (trazabilidad, D-220). Solo cuentan los envíos que llegaron a {@code ENVIADA}: uno que se
 * quedó armando, o una entrega «ya se envió antes» sin datos, no tiene nada que seguir.
 *
 * @param envio     1 el primero, 2 después de un reenvío, y así
 * @param datos     medio, courier, código y costo tal como quedaron al enviarla (el costo NO sale al aprendiz:
 *                  lo filtra la respuesta HTTP)
 * @param resultado el último estado de ese envío: {@code ENVIADA} (sigue en camino), {@code ENTREGADA} o
 *                  {@code CON_PROBLEMA}
 * @param en        cuándo llegó a ese resultado
 * @param motivo    el motivo si terminó con problema; {@code null} en cualquier otro caso
 */
public record EnvioSalido(int envio, DatosDelEnvio datos, EstadoCaja resultado, Instant en,
                          MotivoProblema motivo) {

    public EnvioSalido {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(en, "en");
    }

    /** Los envíos que salieron, del primero al último, a partir de los pasos ordenados del más viejo al más nuevo. */
    public static List<EnvioSalido> de(List<PasoDeCaja> pasos) {
        List<EnvioSalido> salidos = new ArrayList<>();
        pasos.stream().filter(p -> p.tipo() == TipoPasoCaja.ENVIADA).map(PasoDeCaja::envio).distinct().sorted()
                .forEach(envio -> salidos.add(delEnvio(pasos, envio)));
        return List.copyOf(salidos);
    }

    private static EnvioSalido delEnvio(List<PasoDeCaja> pasos, int envio) {
        List<PasoDeCaja> delEnvio = pasos.stream().filter(p -> p.envio() == envio).toList();
        PasoDeCaja enviada = delEnvio.stream().filter(p -> p.tipo() == TipoPasoCaja.ENVIADA)
                .reduce((primero, segundo) -> segundo).orElseThrow();
        PasoDeCaja ultimo = delEnvio.stream().filter(EnvioSalido::esDeSalida)
                .reduce((primero, segundo) -> segundo).orElse(enviada);
        MotivoProblema motivo = ultimo.tipo() == TipoPasoCaja.CON_PROBLEMA
                ? ultimo.dato(PasoDeCaja.MOTIVO).flatMap(MotivoProblema::leido).orElse(null) : null;
        return new EnvioSalido(envio, DatosDelEnvio.desde(enviada),
                ultimo.tipo().estadoDelEnvio().orElseThrow(), ultimo.en(), motivo);
    }

    /** Lo que le pasa a un envío después de salir: sigue en camino, se entregó o tuvo un problema. */
    private static boolean esDeSalida(PasoDeCaja paso) {
        return paso.tipo() == TipoPasoCaja.ENVIADA || paso.tipo() == TipoPasoCaja.ENTREGADA
                || paso.tipo() == TipoPasoCaja.CON_PROBLEMA;
    }
}
