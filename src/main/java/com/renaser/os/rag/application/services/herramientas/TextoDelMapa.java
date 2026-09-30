package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Accion;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Hito;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Objetivo;
import com.renaser.os.rag.domain.model.mapa.ProximoHito;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El texto de {@code consultar_mi_mapa} (D-233): compacto, con las palabras de la persona y sin nada que
 * ella no haya escrito. Los numeros y los hitos salen tal cual; el proximo hito y los dias que faltan los
 * calcula {@link ProximoHito} con el dia de hoy, nunca el modelo.
 */
final class TextoDelMapa {

    /** Un dia de programa antes de este, no tener Mapa es lo esperado: se arma el dia 7. */
    static final int DIA_DEL_MAPA = 7;

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.forLanguageTag("es"));

    private TextoDelMapa() {
    }

    static String completo(MapaDeLaPersona mapa, Optional<SituacionDelAprendiz> situacion) {
        if (!mapa.tieneMapa()) {
            return sinMapa(situacion);
        }
        return String.join("\n", encabezado(mapa), objetivos(mapa), hitos(mapa, situacion), acciones(mapa),
                retorno(mapa), "Usa sus palabras. No agregues metas, numeros ni hitos que no esten aca.");
    }

    static String sinMapa(Optional<SituacionDelAprendiz> situacion) {
        if (situacion.isPresent() && situacion.get().diaPrograma() < DIA_DEL_MAPA) {
            return "Todavia no tiene Mapa de Renacimiento: se arma el dia " + DIA_DEL_MAPA + " del programa y hoy "
                    + "es su dia " + situacion.get().diaPrograma() + ". No le inventes metas ni hitos.";
        }
        return "No tiene Mapa de Renacimiento guardado (no lo recorrio, o lo hizo antes de que se guardara en "
                + "el servidor). No le inventes metas ni hitos: diselo en una linea e invitala a completar su "
                + "Mapa de Renacimiento en la app.";
    }

    static String area(String clave) {
        return MapaDeLaPersona.nombreDeArea(clave);
    }

    private static String encabezado(MapaDeLaPersona mapa) {
        String estado = mapa.completado() ? "" : " (lo dejo a medias: lo que no aparece, no lo escribio)";
        String prioridad = mapa.prioridad() == null ? "no la eligio"
                : area(mapa.prioridad()) + " (el area que manda; las otras siguen contando)";
        return "Mapa de Renacimiento" + estado + ".\nPrioridad: " + prioridad + ".";
    }

    private static String objetivos(MapaDeLaPersona mapa) {
        if (mapa.objetivos().isEmpty()) {
            return "Objetivos: no escribio ninguno.";
        }
        return "Objetivos a 90 dias:\n" + mapa.objetivos().stream()
                .map(TextoDelMapa::objetivo).collect(Collectors.joining("\n"));
    }

    private static String objetivo(Objetivo o) {
        StringBuilder linea = new StringBuilder("- ").append(area(o.area()));
        if (o.tipo() != null) {
            linea.append(" (").append(o.tipo().replace('_', ' ')).append(")");
        }
        linea.append(":");
        agregar(linea, " Meta: \"", o.metaRedactada(), "\".");
        if (o.lineaBase() != null || o.metaDia90() != null) {
            linea.append(" Hoy ").append(conUnidad(o.lineaBase(), o.unidad())).append(" -> dia 90: ")
                    .append(conUnidad(o.metaDia90(), o.unidad()));
            agregar(linea, " (", periodo(o.periodo()), ")");
            linea.append(".");
        }
        agregar(linea, " Va a hacer distinto: ", o.conductaNueva(), ".");
        agregar(linea, " Evidencia: ", o.evidencia(), ".");
        agregar(linea, " Por que: ", o.porque(), ".");
        return linea.toString();
    }

    private static String hitos(MapaDeLaPersona mapa, Optional<SituacionDelAprendiz> situacion) {
        Optional<ProximoHito> proximo = situacion.flatMap(s -> ProximoHito.para(s.diaPrograma()));
        String cabeza = situacion.map(s -> "Hitos, pasos hacia la meta del dia 90 (hoy es su dia " + s.diaPrograma() + " de 90):")
                .orElse("Hitos, pasos hacia la meta del dia 90 (no se su dia del programa: no digas cual es el proximo):");
        String lineas = ProximoHito.DIAS_DE_HITO.stream()
                .map(dia -> lineaDeHito(mapa.hitosDelDia(dia), dia, proximo, situacion))
                .collect(Collectors.joining("\n"));
        return cabeza + "\n" + lineas;
    }

    private static String lineaDeHito(List<Hito> hitos, int dia, Optional<ProximoHito> proximo,
                                      Optional<SituacionDelAprendiz> situacion) {
        String textos = hitos.isEmpty() ? "no escribio hitos para este dia."
                : hitos.stream().map(h -> area(h.area()) + ": " + h.texto()).collect(Collectors.joining(" | "));
        return "- Dia " + dia + marcaDeHito(dia, proximo, situacion) + ": " + textos;
    }

    private static String marcaDeHito(int dia, Optional<ProximoHito> proximo, Optional<SituacionDelAprendiz> situacion) {
        if (proximo.isPresent() && proximo.get().dia() == dia) {
            return " [PROXIMO: " + cuando(proximo.get(), situacion.get()) + "]";
        }
        return situacion.filter(s -> s.diaPrograma() > dia).map(s -> " (ya paso)").orElse("");
    }

    private static String cuando(ProximoHito proximo, SituacionDelAprendiz situacion) {
        if (proximo.faltan() == 0) {
            return "es hoy";
        }
        String faltan = proximo.faltan() == 1 ? "falta 1 dia" : "faltan " + proximo.faltan() + " dias";
        return situacion.hoy() == null ? faltan
                : faltan + ", el " + situacion.hoy().plusDays(proximo.faltan()).format(FECHA);
    }

    private static String acciones(MapaDeLaPersona mapa) {
        if (mapa.acciones().isEmpty()) {
            return "Acciones para llegar: no eligio ninguna en el Mapa.";
        }
        return "Acciones que eligio para llegar: " + mapa.acciones().stream()
                .map(TextoDelMapa::accion).collect(Collectors.joining("; ")) + ".";
    }

    private static String accion(Accion accion) {
        return accion.texto() + " (" + area(accion.area()).toLowerCase(Locale.ROOT) + ", " + accion.vecesPorSemana()
                + (accion.vecesPorSemana() == 1 ? " vez" : " veces") + " por semana)";
    }

    private static String retorno(MapaDeLaPersona mapa) {
        String protocolo = mapa.protocoloDeRetorno() == null
                ? "Protocolo de retorno: no lo escribio."
                : "Protocolo de retorno (su accion minima para volver en menos de 24 h si se cae): \""
                        + mapa.protocoloDeRetorno() + "\"";
        if (mapa.reemplazos().isEmpty()) {
            return protocolo;
        }
        return protocolo + "\nSus reemplazos: " + String.join(" ", mapa.reemplazos());
    }

    private static String conUnidad(String valor, String unidad) {
        if (valor == null) {
            return "sin dato";
        }
        boolean yaLaTrae = unidad != null && valor.toLowerCase(Locale.ROOT).contains(unidad.toLowerCase(Locale.ROOT));
        return unidad == null || yaLaTrae ? valor : valor + " " + unidad;
    }

    private static String periodo(String periodo) {
        if (periodo == null) {
            return null;
        }
        return "acumulado_dia_90".equals(periodo) ? "acumulado al dia 90" : periodo.replace('_', ' ');
    }

    private static void agregar(StringBuilder linea, String antes, String valor, String despues) {
        if (valor != null) {
            linea.append(antes).append(valor).append(despues);
        }
    }
}
