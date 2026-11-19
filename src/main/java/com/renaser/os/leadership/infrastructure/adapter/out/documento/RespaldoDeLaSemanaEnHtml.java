package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.DetalleDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvaluacionDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.FocoDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.HabitoDelDia;

import java.util.List;

import static com.renaser.os.leadership.infrastructure.adapter.out.documento.Html.e;

/**
 * La segunda mitad del documento semanal de una persona (spec 056, FR-035): las evidencias de la semana, «En sus palabras» (solo si escribió
 * algo: no se inventa), la evaluación del mes de su mentor, el foco de la próxima semana y, en una segunda página, el detalle día por día.
 */
final class RespaldoDeLaSemanaEnHtml {

    private RespaldoDeLaSemanaEnHtml() {
    }

    static String evidencias(DatosDelAprendiz datos, String prefijo) {
        if (datos.evidencias().isEmpty()) {
            return Html.seccion(prefijo + "evidencias", "Evidencias de la semana", Html.parrafo("Sin evidencias esta semana."));
        }
        List<List<String>> filas = datos.evidencias().stream().map(evidencia -> List.of(Textos.tipoDeEvidencia(evidencia.tipo()),
                Textos.fechaCorta(evidencia.fecha()), e(Textos.habito(evidencia.habito())), Textos.revision(evidencia.estado()))).toList();
        return Html.seccion(prefijo + "evidencias", "Evidencias de la semana", Html.tabla(List.of("Tipo", "Fecha", "Hábito", "Estado"), filas));
    }

    /** Vacío si la persona no escribió nada esa semana: la sección no se muestra. */
    static String enSusPalabras(DatosDelAprendiz datos, String prefijo) {
        if (datos.palabras().isEmpty()) {
            return "";
        }
        StringBuilder citas = new StringBuilder();
        datos.palabras().forEach(palabra -> citas.append("<blockquote><strong>").append(e(Textos.habito(palabra.habito()))).append("</strong> · ")
                .append(Textos.fechaCorta(palabra.fecha())).append("<br>").append(e(palabra.texto())).append("</blockquote>"));
        return Html.seccion(prefijo + "en-sus-palabras", "En sus palabras", citas.toString());
    }

    /** Vacío si no tiene mentor: no hay a quién evaluar. */
    static String evaluacionDelMentor(DatosDelAprendiz datos, String prefijo) {
        if (datos.encabezado().mentor() == null) {
            return "";
        }
        return Html.seccion(prefijo + "evaluacion-del-mentor", "Evaluación de su mentor",
                Html.parrafo(e(datos.encabezado().mentor()) + " · " + evaluacion(datos.evaluacionDelMentor())));
    }

    private static String evaluacion(EvaluacionDelMentor evaluacion) {
        if (evaluacion == null) {
            return "No disponible";
        }
        return evaluacion.porcentaje() == null ? "Sin evaluación calculada este mes" : "Evaluación del mes " + Textos.evaluacion(evaluacion.porcentaje());
    }

    static String foco(DatosDelAprendiz datos, String prefijo) {
        FocoDeLaSemana foco = datos.foco();
        if (foco == null) {
            return "";
        }
        String frase = foco.pilar() == null ? "Sostener el ritmo de esta semana."
                : e(foco.pilar()) + ": cumplió " + foco.cumplidos() + " de " + Textos.cantidad(foco.programados(), "hábito", "hábitos")
                + ". Es el pilar que más necesita foco.";
        return Html.seccion(prefijo + "foco", "Foco para la próxima semana", Html.parrafo(frase));
    }

    static String detalle(DatosDelAprendiz datos, String prefijo) {
        StringBuilder dias = new StringBuilder();
        datos.detalle().forEach(dia -> dias.append(dia(dia)));
        return Html.seccion(prefijo + "detalle", "segunda-pagina", "Detalle día por día", dias.toString());
    }

    private static String dia(DetalleDelDia dia) {
        String titulo = Textos.fechaConDia(dia.fecha()) + (dia.dia() == null ? "" : " · Día " + dia.dia());
        String motivo = dia.motivo() == null ? "" : " · " + Textos.motivo(dia.motivo());
        String habitos = dia.habitos().isEmpty() ? "" : Html.lista(dia.habitos().stream().map(RespaldoDeLaSemanaEnHtml::habito).toList());
        return "<h3>" + e(titulo) + motivo + "</h3>" + habitos;
    }

    private static String habito(HabitoDelDia habito) {
        String entrega = Textos.entrega(habito.entrega());
        return e(habito.pilar()) + " · " + e(Textos.habito(habito.titulo())) + " · " + Textos.estadoDelHabito(habito.estado())
                + (entrega.isEmpty() ? "" : " · " + entrega);
    }
}
