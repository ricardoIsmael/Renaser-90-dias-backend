package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes.MentorDelRanking;
import com.renaser.os.points.api.ColorSemaforo;

import java.time.Instant;
import java.util.List;

import static com.renaser.os.leadership.infrastructure.adapter.out.documento.Html.e;

/**
 * El reporte del mes del cuerpo de mentores (spec 056, FR-032 y FR-031): el promedio, cuántos están debajo del 60 %, el ranking con el
 * estado de cada mentor —palabra y símbolo, no solo color— y sus consultas respondidas, y los que no tienen evaluación calculada.
 */
final class ReporteDelMesEnHtml {

    private ReporteDelMesEnHtml() {
    }

    static String cuerpo(DatosDelMes datos, Instant generadoEn) {
        StringBuilder cuerpo = new StringBuilder(encabezado(datos, generadoEn)).append(promedio(datos));
        cuerpo.append(datos.ranking().isEmpty() ? sinNadie() : ranking(datos.ranking()));
        if (!datos.sinMuestra().isEmpty()) {
            cuerpo.append(Html.seccion("sin-muestra", "Sin evaluación calculada", Html.lista(datos.sinMuestra().stream().map(Html::e).toList())));
        }
        return cuerpo.toString();
    }

    private static String encabezado(DatosDelMes datos, Instant generadoEn) {
        return "<header data-seccion=\"encabezado\"><p class=\"marca\">RENASER</p><h1>Reporte de " + Textos.mes(datos.desde())
                + "</h1><dl class=\"meta\"><dt>Cuerpo</dt><dd>Mentores</dd><dt>Período</dt><dd>del " + Textos.fechaLarga(datos.desde())
                + " al " + Textos.fechaLarga(datos.hasta()) + "</dd><dt>Generado</dt><dd>" + Textos.generado(generadoEn, datos.zona())
                + "</dd></dl></header>\n";
    }

    private static String promedio(DatosDelMes datos) {
        String promedio = datos.promedio() == null ? "Sin promedio" : Textos.porcentaje(datos.promedio());
        return Html.seccion("promedio", "Promedio de los mentores", "<div class=\"cifras\"><div class=\"cifra\"><span class=\"valor\">" + promedio
                + "</span>Promedio de las evaluaciones</div><div class=\"cifra\"><span class=\"valor\">" + datos.debajoDelUmbral()
                + "</span>Debajo del " + Textos.porcentaje(PeorEvaluacion.UMBRAL) + "</div></div>");
    }

    private static String sinNadie() {
        return Html.seccion("ranking", "Ranking de mentores", Html.parrafo("Sin mentores evaluados este mes."));
    }

    private static String ranking(List<MentorDelRanking> ranking) {
        StringBuilder filas = new StringBuilder();
        for (int puesto = 0; puesto < ranking.size(); puesto++) {
            filas.append(fila(puesto + 1, ranking.get(puesto)));
        }
        return Html.seccion("ranking", "Ranking de mentores", "<table><thead><tr><th scope=\"col\">#</th><th scope=\"col\">Mentor</th>"
                + "<th scope=\"col\">Evaluación</th><th scope=\"col\">Estado</th><th scope=\"col\">Consultas respondidas</th>"
                + "<th scope=\"col\">Observaciones</th></tr></thead><tbody>" + filas + "</tbody></table>");
    }

    private static String fila(int puesto, MentorDelRanking mentor) {
        ColorDeLaFila estado = ColorDeLaFila.de(ColorSemaforo.delPorcentaje(mentor.evaluacion()));
        return "<tr><td class=\"num\">" + puesto + "</td><td>" + e(mentor.nombre()) + "</td><td class=\"num\">" + Textos.porcentaje(mentor.evaluacion())
                + "</td><td><span class=\"estado " + Textos.clase(estado) + "\">" + Textos.simbolo(estado) + " " + e(estado.etiqueta())
                + "</span></td><td class=\"num\">" + (mentor.respondidas() == null ? "No disponible" : mentor.respondidas())
                + "</td><td class=\"num\">" + (mentor.observaciones() == null ? "No disponible" : mentor.observaciones()) + "</td></tr>";
    }
}
