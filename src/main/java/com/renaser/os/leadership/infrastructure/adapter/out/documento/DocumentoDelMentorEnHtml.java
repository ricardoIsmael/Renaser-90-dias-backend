package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.ConteoPorColor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Foco;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.LoQueLeDijoElLider;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Pendientes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.renaser.os.leadership.infrastructure.adapter.out.documento.Html.e;

/**
 * El documento semanal de un mentor (spec 056, FR-036), con las secciones en el orden de la spec: encabezado, titular de su grupo, conteo por
 * color, aprendices por riesgo con su tendencia, pendientes, alertas, lo que el líder le dijo y el foco. Un mentor sin grupo se dice en una
 * línea y no trae conteos ni tablas vacías (FR-038). Si sigue su programa, al final va su propia semana como la de un aprendiz.
 */
final class DocumentoDelMentorEnHtml {

    private DocumentoDelMentorEnHtml() {
    }

    static String cuerpo(DatosDelMentor datos, Instant generadoEn, String suSemana) {
        StringBuilder cuerpo = new StringBuilder(encabezado(datos, generadoEn)).append(titular(datos));
        if (datos.encabezado().grupos() > 0) {
            cuerpo.append(conteo(datos.conteo())).append(aprendices(datos.aprendices())).append(pendientes(datos.pendientes()))
                    .append(alertas(datos)).append(observaciones(datos)).append(foco(datos.foco()));
        } else {
            cuerpo.append(observaciones(datos));
        }
        return cuerpo.append(suSemana).toString();
    }

    private static String encabezado(DatosDelMentor datos, Instant generadoEn) {
        var cabecera = datos.encabezado();
        return "<header data-seccion=\"encabezado\"><p class=\"marca\">RENASER</p><h1>" + e(cabecera.nombre()) + "</h1><dl class=\"meta\">"
                + fila("Rol", "Mentor" + (cabecera.formacion() == null ? "" : " · " + e(cabecera.formacion())))
                + fila("Semana", "del " + Textos.fechaLarga(cabecera.desde()) + " al " + Textos.fechaLarga(cabecera.hasta()))
                + fila("Generado", Textos.generado(generadoEn, cabecera.zona())) + "</dl></header>\n";
    }

    private static String fila(String nombre, String valor) {
        return "<dt>" + nombre + "</dt><dd>" + valor + "</dd>";
    }

    private static String titular(DatosDelMentor datos) {
        var titular = datos.titular();
        if (datos.encabezado().grupos() == 0) {
            return Html.seccion("titular", "Su grupo", "<p><span class=\"estado estado-sin-datos\">· Sin grupo asignado</span></p>");
        }
        String promedio = titular.promedio() == null ? "" : " · promedio " + Textos.porcentaje(titular.promedio());
        String grupos = String.join(", ", titular.grupos().stream().map(Html::e).toList());
        return Html.seccion("titular", "Su grupo", "<p><span class=\"estado " + Textos.clase(titular.estado()) + "\">"
                + Textos.simbolo(titular.estado()) + " " + e(titular.estado().etiqueta()) + "</span>" + promedio + "</p>"
                + Html.parrafo(titular.aprendices() + " aprendices · " + grupos));
    }

    private static String conteo(ConteoPorColor conteo) {
        return Html.seccion("conteo", "Aprendices por color", "<div class=\"cifras\">"
                + cuenta(ColorDeLaFila.ROJO, "En rojo", conteo.rojo()) + cuenta(ColorDeLaFila.AMARILLO, "En ámbar", conteo.ambar())
                + cuenta(ColorDeLaFila.VERDE, "Al día", conteo.verde()) + cuenta(ColorDeLaFila.SIN_DATOS, "Sin datos", conteo.sinDatos()) + "</div>");
    }

    private static String cuenta(ColorDeLaFila estado, String nombre, int cantidad) {
        return "<div class=\"cifra\"><span class=\"valor\">" + Textos.simbolo(estado) + " " + cantidad + "</span>" + nombre + "</div>";
    }

    private static String aprendices(List<AprendizDelMentor> aprendices) {
        List<List<String>> filas = aprendices.stream().map(DocumentoDelMentorEnHtml::fila).toList();
        return Html.seccion("aprendices", "Aprendices por riesgo",
                Html.tabla(List.of("Aprendiz", "Grupo", "Estado", "Semana", "Tendencia"), filas));
    }

    private static List<String> fila(AprendizDelMentor aprendiz) {
        return List.of(e(aprendiz.nombre()), e(aprendiz.grupo()), Textos.simbolo(aprendiz.estado()) + " " + e(aprendiz.estado().etiqueta()),
                aprendiz.porcentaje() == null ? "–" : Textos.porcentaje(aprendiz.porcentaje()), Textos.tendencia(aprendiz.tendencia()));
    }

    private static String pendientes(Pendientes pendientes) {
        return Html.seccion("pendientes", "Pendientes", Html.lista(List.of(consultas(pendientes), evaluacion(pendientes), enRojo(pendientes))));
    }

    private static String consultas(Pendientes pendientes) {
        if (pendientes.consultas() == null) {
            return "Consultas: No disponible";
        }
        String dias = pendientes.diasDeLaMasVieja() == null ? "" : " · la más vieja, de hace " + pendientes.diasDeLaMasVieja() + " días";
        return pendientes.consultas() + " consultas sin responder" + dias;
    }

    private static String evaluacion(Pendientes pendientes) {
        if (pendientes.evaluacionEstado() == null) {
            return "Evaluación del mes: No disponible";
        }
        return "Evaluación del mes: " + (pendientes.evaluacionPorcentaje() == null ? "sin calcular"
                : Textos.porcentaje(pendientes.evaluacionPorcentaje()));
    }

    private static String enRojo(Pendientes pendientes) {
        if (pendientes.enRojoPorEscribir().isEmpty()) {
            return "Ningún aprendiz en rojo al que escribirle";
        }
        return "Escribir a los aprendices en rojo: " + String.join(", ", pendientes.enRojoPorEscribir().stream().map(Html::e).toList());
    }

    private static String alertas(DatosDelMentor datos) {
        if (datos.alertas().isEmpty()) {
            return Html.seccion("alertas", "Alertas", Html.parrafo("Sin alertas esta semana."));
        }
        return Html.seccion("alertas", "Alertas", Html.lista(datos.alertas().stream()
                .map(alerta -> e(alerta.nombre()) + " · " + alerta.diasSinActividad() + " días sin actividad").toList()));
    }

    private static String observaciones(DatosDelMentor datos) {
        if (datos.observaciones().isEmpty()) {
            return Html.seccion("observaciones", "Lo que le dijo el líder", Html.parrafo("Nada esta semana."));
        }
        ZoneId zona = ZoneId.of(datos.zona());
        StringBuilder citas = new StringBuilder();
        for (LoQueLeDijoElLider observacion : datos.observaciones()) {
            LocalDate dia = observacion.escritaEn().atZone(zona).toLocalDate();
            citas.append("<blockquote><strong>").append(Textos.tipoDeObservacion(observacion.tipo())).append("</strong> · ")
                    .append(e(observacion.autor())).append(" · ").append(Textos.fechaCorta(dia)).append("<br>")
                    .append(e(observacion.texto())).append("</blockquote>");
        }
        return Html.seccion("observaciones", "Lo que le dijo el líder", citas.toString());
    }

    private static String foco(Foco foco) {
        String frase = switch (foco.tipo()) {
            case "ESCRIBIR_A_LOS_ROJOS" -> "Escribir a " + foco.cantidad() + (foco.cantidad() == 1 ? " aprendiz" : " aprendices") + " en rojo.";
            case "RESPONDER_CONSULTAS" -> "Responder " + foco.cantidad() + (foco.cantidad() == 1 ? " consulta" : " consultas") + " pendientes.";
            case "REVISAR_ALERTAS" -> "Revisar las alertas de los aprendices sin actividad.";
            default -> "Mantener el ritmo de su grupo.";
        };
        return Html.seccion("foco", "Foco de la semana", Html.parrafo(frase));
    }
}
