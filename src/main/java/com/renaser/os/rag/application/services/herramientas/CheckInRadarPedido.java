package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.RespuestasRadar;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Las cinco respuestas del Codigo Renaser leidas de una invocacion (2026-09-23): la del modelo, al
 * proponer, y la guardada, al confirmar. Una sola lectura para las dos mitades, mismo criterio que
 * {@code PropuestaDeCambioDeHorario.CambioPedido}.
 *
 * <p>Los limites (no vacias, largo maximo, energia 1..10) son los de {@code habits}
 * ({@link DiarioYRadarDelAprendizPort#RADAR_TEXTO_MAXIMO} y compania): se validan antes para no
 * ofrecer un boton que va a fallar, y el caso de uso los vuelve a validar al confirmar. Los textos
 * se guardan recortados de espacios, igual que los guarda {@code RegistroRadar}.
 */
record CheckInRadarPedido(RespuestasRadar respuestas) {

    static final String ARGUMENTO_QUE_HAGO = "que_hago";
    static final String ARGUMENTO_QUE_PIENSO = "que_pienso";
    static final String ARGUMENTO_QUE_SIENTO = "que_siento";
    static final String ARGUMENTO_NIVEL_ENERGIA = "nivel_energia";
    static final String ARGUMENTO_QUE_EVITO = "que_evito";

    /** @throws PropuestaImposibleException con el motivo ya escrito para el modelo */
    static CheckInRadarPedido de(InvocacionHerramienta invocacion) {
        return new CheckInRadarPedido(new RespuestasRadar(
                texto(invocacion, ARGUMENTO_QUE_HAGO, "que esta haciendo"),
                texto(invocacion, ARGUMENTO_QUE_PIENSO, "que esta pensando"),
                texto(invocacion, ARGUMENTO_QUE_SIENTO, "que esta sintiendo"),
                energia(invocacion.argumento(ARGUMENTO_NIVEL_ENERGIA)),
                texto(invocacion, ARGUMENTO_QUE_EVITO, "que esta evitando")));
    }

    /** La invocacion normalizada que se guarda en la propuesta y se ejecuta tal cual al confirmar. */
    InvocacionHerramienta invocacion(String herramienta) {
        return new InvocacionHerramienta(herramienta, Map.of(
                ARGUMENTO_QUE_HAGO, respuestas.queHago(),
                ARGUMENTO_QUE_PIENSO, respuestas.quePienso(),
                ARGUMENTO_QUE_SIENTO, respuestas.queSiento(),
                ARGUMENTO_NIVEL_ENERGIA, Integer.toString(respuestas.nivelEnergia()),
                ARGUMENTO_QUE_EVITO, respuestas.queEvito()));
    }

    private static String texto(InvocacionHerramienta invocacion, String argumento, String pregunta) {
        String valor = invocacion.argumento(argumento);
        if (valor == null || valor.isBlank()) {
            throw new PropuestaImposibleException("Falta la respuesta a '" + pregunta + "': preguntasela a la "
                    + "persona, no la completes tu.");
        }
        String limpio = valor.strip();
        if (limpio.length() > DiarioYRadarDelAprendizPort.RADAR_TEXTO_MAXIMO) {
            throw new PropuestaImposibleException("La respuesta a '" + pregunta + "' supera los "
                    + DiarioYRadarDelAprendizPort.RADAR_TEXTO_MAXIMO + " caracteres: pidele que la acorte.");
        }
        return limpio;
    }

    /**
     * Acepta "4" y tambien "4.0": un modelo suele mandar los numeros de un argumento entero como
     * decimales (el JSON no distingue). "4.5" no es un nivel valido y se rechaza.
     */
    private static int energia(String valor) {
        int nivel;
        try {
            nivel = new BigDecimal(valor == null ? "" : valor.strip()).intValueExact();
        } catch (NumberFormatException | ArithmeticException noEsEntero) {
            throw fueraDeRango();
        }
        if (nivel < DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MINIMA
                || nivel > DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MAXIMA) {
            throw fueraDeRango();
        }
        return nivel;
    }

    private static PropuestaImposibleException fueraDeRango() {
        return new PropuestaImposibleException("El nivel de energia tiene que ser un numero entero del "
                + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MINIMA + " al "
                + DiarioYRadarDelAprendizPort.RADAR_ENERGIA_MAXIMA + ", el que diga la persona.");
    }
}
