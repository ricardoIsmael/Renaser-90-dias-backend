package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.CeldaDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifra;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Encabezado;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FilaDePilar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.renaser.os.leadership.infrastructure.adapter.out.documento.Html.e;

/**
 * La primera mitad del documento semanal de una persona (spec 056, FR-035): encabezado, titular con el estado de la semana, cuatro cifras
 * contra la semana anterior y la tabla de pilares por día. La segunda mitad —evidencias, palabras, evaluación, foco y detalle— es
 * {@link RespaldoDeLaSemanaEnHtml}.
 */
final class ResumenDeLaSemanaEnHtml {

    private ResumenDeLaSemanaEnHtml() {
    }

    static String encabezado(DatosDelAprendiz datos, Instant generadoEn) {
        Encabezado cabecera = datos.encabezado();
        String dia = cabecera.dia() == null ? "Programa sin iniciar" : "Día " + cabecera.dia() + " de " + cabecera.duracionDias();
        return "<header data-seccion=\"encabezado\"><p class=\"marca\">RENASER</p><h1>" + e(cabecera.nombre()) + "</h1><dl class=\"meta\">"
                + fila("Formación", e(cabecera.formacion()) + " · " + dia)
                + fila("Mentor", cabecera.mentor() == null ? "Sin mentor" : e(cabecera.mentor()))
                + fila("Grupo", cabecera.grupo() == null ? "Sin grupo" : e(cabecera.grupo()))
                + fila("Semana", "del " + Textos.fechaLarga(cabecera.desde()) + " al " + Textos.fechaLarga(cabecera.hasta()))
                + fila("Generado", Textos.generado(generadoEn, cabecera.zona())) + "</dl></header>\n";
    }

    private static String fila(String nombre, String valor) {
        return "<dt>" + nombre + "</dt><dd>" + valor + "</dd>";
    }

    static String titular(DatosDelAprendiz datos, String prefijo) {
        var estado = datos.titular().estado();
        String porcentaje = datos.titular().porcentaje() == null ? "" : " · " + Textos.porcentaje(datos.titular().porcentaje()) + " de cumplimiento";
        return Html.seccion(prefijo + "titular", "Estado de la semana", "<p><span class=\"estado " + Textos.clase(estado) + "\">"
                + Textos.simbolo(estado) + " " + e(estado.etiqueta()) + "</span>" + porcentaje + "</p>");
    }

    static String cifras(DatosDelAprendiz datos, String prefijo) {
        var cifras = datos.cifras();
        return Html.seccion(prefijo + "cifras", "Cifras de la semana", "<div class=\"cifras\">"
                + cifra("Hábitos cumplidos", cifras.habitosCumplidos()) + cifra("Días activos", cifras.diasActivos())
                + cifra("Evidencias", cifras.evidencias()) + cifra("Días programados", cifras.diasProgramados()) + "</div>");
    }

    private static String cifra(String nombre, Cifra cifra) {
        return "<div class=\"cifra\"><span class=\"valor\">" + cifra.valor() + "</span>" + nombre + "<span class=\"cambio\">"
                + cambio(cifra.diferencia()) + "</span></div>";
    }

    private static String cambio(Integer diferencia) {
        if (diferencia == null) {
            return "sin semana anterior";
        }
        if (diferencia == 0) {
            return "igual que la semana anterior";
        }
        return (diferencia > 0 ? "+" + diferencia : "−" + -diferencia) + " frente a la semana anterior";
    }

    static String tablaDePilares(DatosDelAprendiz datos, String prefijo) {
        List<String> encabezados = new ArrayList<>(List.of("Pilar"));
        datos.tabla().dias().forEach(dia -> encabezados.add(Textos.diaCorto(dia)));
        List<List<String>> filas = datos.tabla().filas().stream().map(ResumenDeLaSemanaEnHtml::filaDePilar).toList();
        return Html.seccion(prefijo + "tabla-de-pilares", "Hábitos por pilar y día", Html.tabla(encabezados, filas));
    }

    private static List<String> filaDePilar(FilaDePilar fila) {
        List<String> celdas = new ArrayList<>(List.of(e(fila.pilar())));
        fila.celdas().forEach(celda -> celdas.add(celda(celda)));
        return celdas;
    }

    /** Cumplidos sobre programados; un día sin nada programado en ese pilar se dice con una raya. */
    private static String celda(CeldaDelDia celda) {
        return celda.programados() == 0 ? "–" : celda.cumplidos() + "/" + celda.programados();
    }
}
