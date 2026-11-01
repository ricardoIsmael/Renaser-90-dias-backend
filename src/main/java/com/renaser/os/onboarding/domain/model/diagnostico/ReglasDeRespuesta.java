package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Qué respuestas acepta cada tipo de pregunta del diagnóstico y cuándo cuenta una como respondida. Sin Spring. */
public final class ReglasDeRespuesta {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private ReglasDeRespuesta() {
    }

    /**
     * Valida una respuesta contra su pregunta y la devuelve con SOLO el valor que le corresponde. Una respuesta vacía vuelve igual, vacía: quien guarda
     * entiende «borrar la respuesta».
     *
     * @throws IllegalArgumentException (400) si no cuadra, con el motivo
     */
    public static RespuestaEntrante validar(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        if (r.estaVacia()) {
            return new RespuestaEntrante(r.preguntaId(), null, null, null, null, null);
        }
        return switch (p.tipo()) {
            case TEXTO, AREA_TEXTO -> texto(p, r);
            case NUMERO -> numero(r);
            case FECHA -> fecha(r);
            case SELECCION_UNICA -> unica(p, r);
            case SELECCION_MULTIPLE -> multiple(p, r);
            case ESCALA -> escala(p, r);
            case CUADRICULA -> cuadricula(p, r);
            case CASILLA -> casilla(r);
            default -> throw new IllegalArgumentException("Esta pregunta no se responde aquí");
        };
    }

    /** Si la respuesta guardada cuenta como contestada. Una cuadrícula obligatoria cuenta solo con todas sus filas. */
    public static boolean estaContestada(PreguntaDelDiagnostico p, Respuesta guardada) {
        if (guardada == null) {
            return false;
        }
        return switch (p.tipo()) {
            case TEXTO, AREA_TEXTO, FECHA, SELECCION_UNICA -> guardada.valorTexto() != null && !guardada.valorTexto().isBlank();
            case NUMERO -> guardada.valorNumero() != null;
            case ESCALA -> guardada.valorEscala() != null;
            case CASILLA -> Boolean.TRUE.equals(guardada.valorBooleano());
            case SELECCION_MULTIPLE -> guardada.valorJson() != null && !lista(guardada.valorJson()).isEmpty();
            case CUADRICULA -> guardada.valorJson() != null && filasRespondidas(p, guardada.valorJson()) == p.opciones().size();
            default -> false;
        };
    }

    /** Los valores elegidos en una pregunta de opciones (única o casillas), para evaluar «se muestra solo si». */
    public static Set<String> valoresElegidos(PreguntaDelDiagnostico p, Respuesta guardada) {
        if (guardada == null) {
            return Set.of();
        }
        try {
            return switch (p.tipo()) {
                case SELECCION_UNICA -> guardada.valorTexto() == null ? Set.of() : Set.of(guardada.valorTexto());
                case SELECCION_MULTIPLE -> guardada.valorJson() == null ? Set.of() : new HashSet<>(lista(guardada.valorJson()));
                default -> Set.of();
            };
        } catch (IllegalArgumentException e) {
            return Set.of();
        }
    }

    private static RespuestaEntrante texto(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        requerirSolo(r, true, false, false, false, false);
        String t = r.valorTexto().trim();
        int tope = p.largoMaximo() != null ? p.largoMaximo() : PreguntaDelDiagnostico.MAX_RESPUESTA_LIBRE;
        if (t.length() > tope) {
            throw new IllegalArgumentException("La respuesta no puede pasar de " + tope + " caracteres");
        }
        return new RespuestaEntrante(r.preguntaId(), t, null, null, null, null);
    }

    private static RespuestaEntrante numero(RespuestaEntrante r) {
        requerirSolo(r, false, true, false, false, false);
        return new RespuestaEntrante(r.preguntaId(), null, r.valorNumero(), null, null, null);
    }

    private static RespuestaEntrante fecha(RespuestaEntrante r) {
        requerirSolo(r, true, false, false, false, false);
        try {
            LocalDate.parse(r.valorTexto().trim(), FECHA);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La fecha tiene que ser un día real, como 25/12/1990");
        }
        return new RespuestaEntrante(r.preguntaId(), r.valorTexto().trim(), null, null, null, null);
    }

    private static RespuestaEntrante unica(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        requerirSolo(r, true, false, false, false, false);
        if (!p.tieneOpcion(r.valorTexto())) {
            throw new IllegalArgumentException("Esa opción no existe en la pregunta");
        }
        return new RespuestaEntrante(r.preguntaId(), r.valorTexto(), null, null, null, null);
    }

    private static RespuestaEntrante multiple(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        requerirSolo(r, false, false, false, false, true);
        List<String> elegidas = lista(r.valorJson());
        if (new HashSet<>(elegidas).size() != elegidas.size()) {
            throw new IllegalArgumentException("Una opción está marcada dos veces");
        }
        for (String valor : elegidas) {
            if (!p.tieneOpcion(valor)) {
                throw new IllegalArgumentException("Esa opción no existe en la pregunta");
            }
        }
        if (p.maximoOpciones() != null && elegidas.size() > p.maximoOpciones()) {
            throw new IllegalArgumentException("Elige como máximo " + p.maximoOpciones());
        }
        return new RespuestaEntrante(r.preguntaId(), null, null, null, null, ValoresJson.deLista(elegidas));
    }

    private static RespuestaEntrante escala(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        requerirSolo(r, false, false, false, true, false);
        if (!p.escala().contiene(r.valorEscala())) {
            throw new IllegalArgumentException("El puntaje va de " + p.escala().min() + " a " + p.escala().max());
        }
        return new RespuestaEntrante(r.preguntaId(), null, null, null, r.valorEscala(), null);
    }

    private static RespuestaEntrante cuadricula(PreguntaDelDiagnostico p, RespuestaEntrante r) {
        requerirSolo(r, false, false, false, false, true);
        Map<String, Integer> filas = ValoresJson.objetoDeEnteros(r.valorJson());
        for (Map.Entry<String, Integer> fila : filas.entrySet()) {
            if (!p.tieneOpcion(fila.getKey())) {
                throw new IllegalArgumentException("Esa fila no existe en la pregunta");
            }
            if (!p.escala().contiene(fila.getValue())) {
                throw new IllegalArgumentException("El puntaje va de " + p.escala().min() + " a " + p.escala().max());
            }
        }
        return new RespuestaEntrante(r.preguntaId(), null, null, null, null, ValoresJson.deObjeto(filas));
    }

    private static RespuestaEntrante casilla(RespuestaEntrante r) {
        requerirSolo(r, false, false, true, false, false);
        return new RespuestaEntrante(r.preguntaId(), null, null, Boolean.TRUE, null, null);
    }

    private static void requerirSolo(RespuestaEntrante r, boolean texto, boolean numero, boolean booleano, boolean escala, boolean json) {
        boolean ok = (r.valorTexto() != null && !r.valorTexto().isBlank()) == texto
                && (r.valorNumero() != null) == numero
                && Boolean.TRUE.equals(r.valorBooleano()) == booleano
                && (r.valorEscala() != null) == escala
                && (r.valorJson() != null && !r.valorJson().isBlank()) == json;
        if (!ok) {
            throw new IllegalArgumentException("La respuesta no corresponde al tipo de la pregunta");
        }
    }

    private static List<String> lista(String json) {
        return ValoresJson.lista(json);
    }

    private static int filasRespondidas(PreguntaDelDiagnostico p, String json) {
        try {
            Map<String, Integer> filas = ValoresJson.objetoDeEnteros(json);
            return (int) p.opciones().stream().filter(o -> filas.containsKey(o.valor())).count();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
