package com.renaser.os.rag.domain.model.mapa;

import java.util.List;
import java.util.Optional;

/**
 * El Mapa de Renacimiento de la persona, visto desde el acompanante (D-233). Espejo de
 * {@code onboarding.api.MapaDeRenacimientoFinder.MapaDeRenacimiento}: {@code rag} no expone tipos de otro
 * modulo, y el adaptador traduce campo por campo.
 *
 * <p>Los valores son los que la persona escribio, sin interpretar. Las areas y los tipos llegan con los
 * codigos de la V41 ({@code salud}, {@code negocio_dinero}, {@code peso}...).
 */
public record MapaDeLaPersona(boolean tieneMapa, boolean completado, String prioridad, List<Objetivo> objetivos,
                              List<Hito> hitos, String protocoloDeRetorno, List<Accion> acciones,
                              List<String> reemplazos) {

    public MapaDeLaPersona {
        objetivos = List.copyOf(objetivos);
        hitos = List.copyOf(hitos);
        acciones = List.copyOf(acciones);
        reemplazos = List.copyOf(reemplazos);
    }

    public static MapaDeLaPersona sinMapa() {
        return new MapaDeLaPersona(false, false, null, List.of(), List.of(), null, List.of(), List.of());
    }

    /** Los hitos de ese dia (30, 60 o 90), en el orden salud, negocio, relaciones. */
    public List<Hito> hitosDelDia(int dia) {
        return hitos.stream().filter(hito -> hito.dia() == dia).toList();
    }

    /** El hito de ese dia en el area que manda; si esa area no lo tiene, el primero de ese dia. */
    public Optional<Hito> hitoPrincipalDelDia(int dia) {
        List<Hito> delDia = hitosDelDia(dia);
        return delDia.stream().filter(hito -> hito.area().equals(prioridad)).findFirst()
                .or(() -> delDia.stream().findFirst());
    }

    /**
     * El area del Mapa que corresponde a un eje de las rocas: CUERPO es salud, TRABAJO es negocio y dinero,
     * RELACIONES es relaciones. Es la misma correspondencia que usa {@code rocks} para repartir la Roca
     * Maestra por mes ({@code ObjetivoDelMesService}). {@code null} si el eje no es ninguno de esos.
     */
    public static String areaDelEje(String eje) {
        if (eje == null) {
            return null;
        }
        return switch (eje.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CUERPO" -> "salud";
            case "TRABAJO" -> "negocio_dinero";
            case "RELACIONES" -> "relaciones";
            default -> null;
        };
    }

    /** El objetivo que escribio para esa area, si lo escribio. */
    public Optional<Objetivo> objetivoDelArea(String area) {
        return objetivos.stream().filter(objetivo -> objetivo.area().equals(area)).findFirst();
    }

    /** "Salud", "Negocio y dinero", "Relaciones"; un codigo desconocido sale tal cual, sin guiones bajos. */
    public static String nombreDeArea(String clave) {
        if (clave == null) {
            return "sin area";
        }
        return switch (clave) {
            case "salud" -> "Salud";
            case "negocio_dinero" -> "Negocio y dinero";
            case "relaciones" -> "Relaciones";
            default -> clave.replace('_', ' ');
        };
    }

    /** Ver {@code onboarding.api.MapaDeRenacimientoFinder.ObjetivoDelMapa}: todo campo puede faltar. */
    public record Objetivo(String area, String tipo, String lineaBase, String metaDia90, String unidad,
                           String periodo, String conductaNueva, String evidencia, String porque,
                           String metaRedactada) {

        /** La meta en una frase: la redactada; si no la escribio, "de 92 a 85 kg"; si tampoco, el tipo. */
        public String enUnaFrase() {
            if (metaRedactada != null) {
                return metaRedactada;
            }
            if (metaDia90 != null) {
                String sufijo = unidad == null ? "" : " " + unidad;
                return (lineaBase == null ? "llegar a " : "de " + lineaBase + sufijo + " a ") + metaDia90 + sufijo;
            }
            return tipo == null ? "sin meta escrita" : tipo.replace('_', ' ');
        }
    }

    public record Hito(String area, int dia, String texto) {
    }

    public record Accion(String area, String texto, int vecesPorSemana) {
    }
}
