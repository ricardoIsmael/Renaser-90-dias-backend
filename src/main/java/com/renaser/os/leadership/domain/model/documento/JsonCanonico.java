package com.renaser.os.leadership.domain.model.documento;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * La forma única en que se escriben los datos de un documento para sacar su huella (spec 056, FR-039): los mismos datos dan siempre
 * los mismos caracteres y un dato distinto, otros. Es JSON sin espacios: los registros salen con sus campos en el orden en que se
 * declararon, los mapas con las claves ordenadas, los números sin ceros de sobra, y las fechas y los enums como texto.
 *
 * <p>Se escribe a mano y no con Jackson porque {@code domain/} no lo conoce, y porque acá no importa que el JSON se pueda leer de
 * vuelta: solo que sea estable. Un tipo que no sabe escribir falla fuerte, no se ignora en silencio: una huella que se olvida de un
 * dato reutilizaría un documento viejo.
 */
final class JsonCanonico {

    private JsonCanonico() {
    }

    static String de(Object valor) {
        StringBuilder salida = new StringBuilder();
        escribir(valor, salida);
        return salida.toString();
    }

    private static void escribir(Object valor, StringBuilder salida) {
        switch (valor) {
            case null -> salida.append("null");
            case String texto -> texto(texto, salida);
            case Boolean verdadero -> salida.append(verdadero);
            case BigDecimal decimal -> salida.append(decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString());
            case Number numero -> salida.append(numero);
            case Enum<?> constante -> texto(constante.name(), salida);
            case TemporalAccessor momento -> texto(momento.toString(), salida);
            case Collection<?> lista -> lista(lista, salida);
            case Map<?, ?> mapa -> mapa(mapa, salida);
            case Record registro -> registro(registro, salida);
            default -> throw new IllegalArgumentException("No se sabe escribir en la huella un " + valor.getClass().getName());
        }
    }

    private static void lista(Collection<?> elementos, StringBuilder salida) {
        salida.append('[');
        boolean primero = true;
        for (Object elemento : elementos) {
            if (!primero) {
                salida.append(',');
            }
            primero = false;
            escribir(elemento, salida);
        }
        salida.append(']');
    }

    private static void mapa(Map<?, ?> mapa, StringBuilder salida) {
        Map<String, Object> ordenado = new TreeMap<>();
        mapa.forEach((clave, valor) -> ordenado.put(String.valueOf(clave), valor));
        salida.append('{');
        boolean primero = true;
        for (Map.Entry<String, Object> entrada : ordenado.entrySet()) {
            primero = separar(primero, salida);
            texto(entrada.getKey(), salida);
            salida.append(':');
            escribir(entrada.getValue(), salida);
        }
        salida.append('}');
    }

    private static void registro(Record registro, StringBuilder salida) {
        salida.append('{');
        boolean primero = true;
        for (RecordComponent componente : registro.getClass().getRecordComponents()) {
            primero = separar(primero, salida);
            texto(componente.getName(), salida);
            salida.append(':');
            escribir(leer(componente, registro), salida);
        }
        salida.append('}');
    }

    private static boolean separar(boolean primero, StringBuilder salida) {
        if (!primero) {
            salida.append(',');
        }
        return false;
    }

    private static Object leer(RecordComponent componente, Record registro) {
        try {
            componente.getAccessor().setAccessible(true);
            return componente.getAccessor().invoke(registro);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("No se pudo leer " + componente.getName() + " para la huella", e);
        }
    }

    private static void texto(String texto, StringBuilder salida) {
        salida.append('"');
        for (char letra : texto.toCharArray()) {
            switch (letra) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (letra < 0x20) {
                        salida.append(String.format("\\u%04x", (int) letra));
                    } else {
                        salida.append(letra);
                    }
                }
            }
        }
        salida.append('"');
    }
}
