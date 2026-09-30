package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * El Mapa de Renacimiento (Dia 7, flujo {@code mapa_dia7}, V41) <b>completo</b> de una persona, para
 * leerlo desde otro modulo (D-233).
 *
 * <p><b>Por que existe.</b> {@link MedicionDelMapaFinder} da solo como se mide cada objetivo, que es lo
 * que {@code rocks} necesita para repartir la meta por mes. El acompanante (SER) necesita lo demas: la
 * prioridad, la meta redactada, la linea base y la meta al dia 90, la evidencia, el porque, los nueve
 * hitos, el protocolo de retorno, las acciones motoras y los reemplazos. Sin esto, a "¿para que me sirve
 * caminar?" contestaba con generalidades en vez de con lo que la persona se propuso.
 *
 * <p>Los valores viajan como <b>texto crudo</b>, tal como los guarda el motor de onboarding (codigos de la
 * V41 como {@code salud} o {@code peso}), con el mismo criterio que {@link MedicionDelMapaFinder}: este
 * modulo no los interpreta para nadie.
 */
public interface MapaDeRenacimientoFinder {

    /**
     * Nunca {@code null} y nunca falla por no haberlo recorrido: eso es el estado normal de todo el que no
     * llego al dia 7, y sale como {@link MapaDeRenacimiento#sinMapa()}.
     */
    MapaDeRenacimiento delParticipante(UserId usuarioId);

    /**
     * @param recorrido          {@code false} = no contesto ni una pregunta del Mapa
     * @param completado         si dio la etapa por terminada ({@code etapas_onboarding_completadas})
     * @param prioridad          {@code map_priority_area}: salud, negocio_dinero o relaciones
     * @param objetivos          uno por area que haya contestado, en el orden salud, negocio, relaciones
     * @param hitos              los de los dias 30, 60 y 90 que haya escrito, por area y dia
     * @param protocoloDeRetorno {@code map_return_protocol}: su accion minima para volver en menos de 24 h
     * @param acciones           las acciones motoras (V06), con el area del objetivo al que empujan
     * @param reemplazos         los protocolos de reemplazo, ya como frase ("Cuando..., en lugar de...")
     */
    record MapaDeRenacimiento(boolean recorrido, boolean completado, String prioridad, List<ObjetivoDelMapa> objetivos,
                              List<HitoDelMapa> hitos, String protocoloDeRetorno, List<AccionDelMapa> acciones,
                              List<String> reemplazos) {

        public MapaDeRenacimiento {
            objetivos = List.copyOf(objetivos);
            hitos = List.copyOf(hitos);
            acciones = List.copyOf(acciones);
            reemplazos = List.copyOf(reemplazos);
        }

        public static MapaDeRenacimiento sinMapa() {
            return new MapaDeRenacimiento(false, false, null, List.of(), List.of(), null, List.of(), List.of());
        }
    }

    /**
     * Un objetivo tal como lo escribio. Cualquier campo que no haya contestado viene en {@code null}.
     *
     * @param area          salud, negocio_dinero o relaciones
     * @param tipo          el resultado que mueve: peso, facturacion... En relaciones, el vinculo (pareja...)
     * @param lineaBase     como esta hoy. En relaciones, el puntaje de 1 a 10
     * @param metaDia90     a donde quiere llegar al dia 90. En relaciones, el puntaje de 1 a 10
     * @param unidad        la unidad (salud), la moneda (negocio) o "de 10" (relaciones)
     * @param periodo       solo negocio: semanal, mensual o acumulado_dia_90
     * @param conductaNueva solo relaciones: que va a hacer distinto
     * @param evidencia     con que lo va a evidenciar
     * @param porque        por que este resultado y no otro
     * @param metaRedactada la meta en una frase
     */
    record ObjetivoDelMapa(String area, String tipo, String lineaBase, String metaDia90, String unidad,
                           String periodo, String conductaNueva, String evidencia, String porque,
                           String metaRedactada) {
    }

    /** @param dia 30, 60 o 90 */
    record HitoDelMapa(String area, int dia, String texto) {
    }

    record AccionDelMapa(String area, String texto, int vecesPorSemana) {
    }
}
