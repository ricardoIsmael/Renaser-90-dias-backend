package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.util.List;
import java.util.Map;

/**
 * Una respuesta en palabras, para leerla en la ficha de una persona o dársela a SER: el texto de la opción elegida y no su código, las filas de la cuadrícula
 * con su puntaje, «Aceptó» en el consentimiento.
 */
public record RespuestaLegible(String texto, List<String> valores, List<Fila> filas) {

    public RespuestaLegible {
        valores = List.copyOf(valores);
        filas = List.copyOf(filas);
    }

    public record Fila(String etiqueta, int valor) {
    }

    /** {@code null} si no hay respuesta o no se puede leer. */
    public static RespuestaLegible de(PreguntaDelDiagnostico p, Respuesta r) {
        if (!ReglasDeRespuesta.estaContestada(p, r) && !(p.tipo() == com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding.CUADRICULA
                && r != null && r.valorJson() != null)) {
            return null;
        }
        try {
            return switch (p.tipo()) {
                case TEXTO, AREA_TEXTO, FECHA -> new RespuestaLegible(r.valorTexto(), List.of(), List.of());
                case NUMERO -> new RespuestaLegible(r.valorNumero().stripTrailingZeros().toPlainString(), List.of(), List.of());
                case ESCALA -> new RespuestaLegible(r.valorEscala() + " de " + p.escala().max(), List.of(), List.of());
                case SELECCION_UNICA -> new RespuestaLegible(etiquetaDe(p, r.valorTexto()), List.of(), List.of());
                case SELECCION_MULTIPLE -> new RespuestaLegible(null, ValoresJson.lista(r.valorJson()).stream().map(v -> etiquetaDe(p, v)).toList(), List.of());
                case CUADRICULA -> new RespuestaLegible(null, List.of(), filas(p, ValoresJson.objetoDeEnteros(r.valorJson())));
                case CASILLA -> new RespuestaLegible("Aceptó", List.of(), List.of());
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Fila> filas(PreguntaDelDiagnostico p, Map<String, Integer> puntajes) {
        return p.opciones().stream().filter(o -> puntajes.containsKey(o.valor())).map(o -> new Fila(o.etiqueta(), puntajes.get(o.valor()))).toList();
    }

    private static String etiquetaDe(PreguntaDelDiagnostico p, String valor) {
        return p.opciones().stream().filter(o -> o.valor().equals(valor)).map(OpcionDelDiagnostico::etiqueta).findFirst().orElse(valor);
    }
}
