package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.application.ports.out.RenderizarDocumentoPort;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDelDocumento;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.renaser.os.leadership.infrastructure.adapter.out.documento.Html.e;

/**
 * El HTML de los tres documentos del liderazgo (spec 056, FR-035, FR-036 y FR-032), con text blocks y sin librerías, como los correos de
 * {@code users}. Es el único lugar que decide cómo se ve un documento; lo que dice sale de {@link DatosDelDocumento}.
 *
 * <p><b>Todo texto de una persona entra por {@link Html#e}</b> —nombres, grupos, lo que escribió, lo que el líder le dijo— que lo pasa por
 * {@code HtmlUtils.htmlEscape}: lo escribe un usuario y el documento lo abre un navegador, así que nada de eso puede llegar como HTML. La
 * nota privada del mentor no tiene dónde entrar (FR-037), y ningún plazo está escrito a mano: el de cada persona viene en sus datos.
 */
@Component
class PlantillaDelDocumento implements RenderizarDocumentoPort {

    private static final String SEMANA_PROPIA = "su-semana";

    @Override
    public String renderizar(DatosDelDocumento datos, Instant generadoEn) {
        return switch (datos) {
            case DatosDelAprendiz aprendiz -> PaginaDelDocumento.armar(titulo("Semana de ", aprendiz.encabezado().nombre(), aprendiz),
                    ResumenDeLaSemanaEnHtml.encabezado(aprendiz, generadoEn) + semana(aprendiz, ""));
            case DatosDelMentor mentor -> PaginaDelDocumento.armar(titulo("Semana del mentor ", mentor.encabezado().nombre(), mentor),
                    DocumentoDelMentorEnHtml.cuerpo(mentor, generadoEn, suSemana(mentor)));
            case DatosDelMes mes -> PaginaDelDocumento.armar("Reporte de " + Textos.mes(mes.desde()), ReporteDelMesEnHtml.cuerpo(mes, generadoEn));
        };
    }

    private static String titulo(String inicio, String nombre, DatosDelDocumento datos) {
        return e(inicio + nombre) + " · " + Textos.fechaCorta(datos.desde()) + " al " + Textos.fechaCorta(datos.hasta());
    }

    /** El resto del documento de una persona: el titular y, si la semana tuvo algo programado, todo lo demás; si no, una línea (FR-038). */
    private static String semana(DatosDelAprendiz datos, String prefijo) {
        String titular = ResumenDeLaSemanaEnHtml.titular(datos, prefijo);
        if (datos.sinActividad()) {
            return titular + Html.seccion(prefijo + "sin-actividad", "Semana", "<p class=\"vacio\">Sin actividad esta semana</p>");
        }
        return titular + ResumenDeLaSemanaEnHtml.cifras(datos, prefijo) + ResumenDeLaSemanaEnHtml.tablaDePilares(datos, prefijo)
                + RespaldoDeLaSemanaEnHtml.evidencias(datos, prefijo) + RespaldoDeLaSemanaEnHtml.enSusPalabras(datos, prefijo)
                + RespaldoDeLaSemanaEnHtml.evaluacionDelMentor(datos, prefijo) + RespaldoDeLaSemanaEnHtml.foco(datos, prefijo)
                + RespaldoDeLaSemanaEnHtml.detalle(datos, prefijo);
    }

    /** La semana propia de un mentor que sigue su programa, como la de un aprendiz y con sus secciones aparte; vacío si no la sigue. */
    private static String suSemana(DatosDelMentor mentor) {
        if (mentor.suSemana() == null) {
            return "";
        }
        return Html.seccion(SEMANA_PROPIA, "Su propia semana", "") + semana(mentor.suSemana(), SEMANA_PROPIA + ".");
    }
}
