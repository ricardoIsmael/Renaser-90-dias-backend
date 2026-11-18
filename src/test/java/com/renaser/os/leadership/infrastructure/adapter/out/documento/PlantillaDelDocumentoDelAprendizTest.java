package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifra;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Cifras;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.DetalleDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Encabezado;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvidenciaDeLaSemana;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.HabitoDelDia;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.PalabraDeLaPersona;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Titular;
import com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.SABADO;
import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.VIERNES;
import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.ZONA;
import static org.assertj.core.api.Assertions.assertThat;

/** El HTML del documento semanal del aprendiz (spec 056, FR-035, FR-037 y FR-038): sus secciones, su orden y lo que nunca lleva. */
class PlantillaDelDocumentoDelAprendizTest {

    /** 03:00 UTC del 18 de noviembre: en Lima es el 17 a las 22:00 (regla 02). */
    private static final Instant GENERADO = Instant.parse("2026-11-18T03:00:00Z");

    private final PlantillaDelDocumento plantilla = new PlantillaDelDocumento();

    private String html(DatosDelAprendiz datos) {
        return plantilla.renderizar(datos, GENERADO);
    }

    @Test
    @DisplayName("spec 056 FR-035: trae las secciones del aprendiz en el orden de la spec: encabezado, titular, cifras, tabla, evidencias, en sus palabras, evaluación del mentor, foco y detalle")
    void lasSeccionesEnOrden() {
        assertThat(SeccionesDelHtml.de(html(DatosDePruebaDelDocumento.aprendiz()))).containsExactly("encabezado", "titular", "cifras",
                "tabla-de-pilares", "evidencias", "en-sus-palabras", "evaluacion-del-mentor", "foco", "detalle");
    }

    @Test
    @DisplayName("spec 056 FR-035: el encabezado trae el nombre, la formación, el día, el mentor, el grupo, la semana y la fecha de generación en la zona de la persona")
    void elEncabezado() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("Ana Pérez", "Escuela Renaser", "Día 47 de 365", "Luisa Rojas", "Fénix")
                .contains("14 de noviembre de 2026").contains("20 de noviembre de 2026")
                .as("generado el 17 de noviembre a las 22:00 en Lima, no el 18 de UTC").contains("17 de noviembre de 2026, 22:00")
                .doesNotContain("18 de noviembre de 2026");
    }

    @Test
    @DisplayName("spec 056 FR-035: sin mentor, sin grupo y sin día lo dice en vez de dejar un hueco")
    void sinMentorSinGrupoSinDia() {
        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        Encabezado e = base.encabezado();
        DatosDelAprendiz datos = new DatosDelAprendiz(new Encabezado(e.nombre(), e.formacion(), null, e.duracionDias(), null, null, e.desde(),
                e.hasta(), e.zona()), base.titular(), base.cifras(), base.tabla(), base.evidencias(), base.palabras(), null,
                base.foco(), base.detalle());

        String html = html(datos);

        assertThat(html).contains("Sin mentor").contains("Sin grupo").doesNotContain("Día null").doesNotContain(">null<");
    }

    @Test
    @DisplayName("spec 056 FR-035: el plazo sale de la formación de la persona, nunca de un texto fijo: con 365 días dice 365 y jamás «90 días»")
    void elPlazoEsElDeLaFormacion() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("de 365").doesNotContainPattern("(?i)\\b90\\s*d[ií]as");
    }

    @Test
    @DisplayName("spec 056 FR-035: ni con un programa de 90 días aparece «90 días» escrito a mano: sale el número de sus datos")
    void conNoventaTampocoSeEscribeAMano() {
        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        Encabezado e = base.encabezado();
        DatosDelAprendiz datos = new DatosDelAprendiz(new Encabezado(e.nombre(), e.formacion(), 12, 90, e.mentor(), e.grupo(), e.desde(),
                e.hasta(), e.zona()), base.titular(), base.cifras(), base.tabla(), base.evidencias(), base.palabras(),
                base.evaluacionDelMentor(), base.foco(), base.detalle());

        assertThat(html(datos)).contains("Día 12 de 90").doesNotContainPattern("(?i)\\b90\\s*d[ií]as");
    }

    @Test
    @DisplayName("spec 056 FR-035: el titular dice el estado con su palabra y su símbolo, no solo con el color, y el porcentaje de la semana")
    void elTitularNoEsSoloColor() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("Requiere atención").contains("66.7 %").contains("!");
        assertThat(html(conEstado(ColorDeLaFila.ROJO, "30"))).contains("Con problemas").contains("×");
        assertThat(html(conEstado(ColorDeLaFila.VERDE, "95"))).contains("Al día").contains("✓");
        assertThat(html(conEstado(ColorDeLaFila.SIN_DATOS, null))).contains("Sin datos").contains("·");
        assertThat(html(conEstado(ColorDeLaFila.NO_SE_MIDE, null))).contains("No se mide en el semáforo");
    }

    @Test
    @DisplayName("spec 056 FR-035: las cuatro cifras se comparan con la semana anterior: sube, baja, igual, o «sin semana anterior» antes de su Día 1")
    void lasCuatroCifrasContraLaAnterior() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("Hábitos cumplidos", "Días activos", "Evidencias", "Días programados")
                .contains("+2", "\u22121", "sin semana anterior").contains("igual");
    }

    @Test
    @DisplayName("spec 056 FR-035: la tabla de pilares trae un pilar por fila y un día por columna con cumplidos sobre programados")
    void laTablaDePilares() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("Cuerpo", "Emociones").contains("sáb 14 nov", "vie 20 nov").contains("1/1", "0/1");
    }

    @Test
    @DisplayName("spec 056 FR-035: las evidencias traen su tipo, su fecha y su estado en palabras")
    void lasEvidencias() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("Foto", "Audio").contains("Verificada", "Pendiente de revisión").contains("14 nov", "16 nov");
    }

    @Test
    @DisplayName("spec 056 FR-035: «En sus palabras» trae lo que la persona escribió y no aparece si no escribió nada: no se inventa")
    void enSusPalabras() {
        assertThat(html(DatosDePruebaDelDocumento.aprendiz())).contains("En sus palabras").contains("Me sentí en calma después de caminar.");

        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        DatosDelAprendiz sinPalabras = new DatosDelAprendiz(base.encabezado(), base.titular(), base.cifras(), base.tabla(), base.evidencias(),
                List.of(), base.evaluacionDelMentor(), base.foco(), base.detalle());
        assertThat(SeccionesDelHtml.de(html(sinPalabras))).doesNotContain("en-sus-palabras");
        assertThat(html(sinPalabras)).doesNotContain("En sus palabras");
    }

    @Test
    @DisplayName("spec 056 FR-035: la evaluación del mes de su mentor dice el porcentaje; sin calcular, lo dice en palabras; sin mentor, también")
    void laEvaluacionDelMentor() {
        assertThat(html(DatosDePruebaDelDocumento.aprendiz())).contains("Evaluación de su mentor", "82.5 %");

        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        DatosDelAprendiz sinCalcular = new DatosDelAprendiz(base.encabezado(), base.titular(), base.cifras(), base.tabla(), base.evidencias(),
                base.palabras(), new DatosDelAprendiz.EvaluacionDelMentor("2026-11", null, "SIN_MUESTRA"), base.foco(), base.detalle());
        assertThat(html(sinCalcular)).contains("Sin evaluación calculada este mes").doesNotContain("null");
    }

    @Test
    @DisplayName("spec 056 FR-035: el foco de la próxima semana nombra el pilar más flojo con lo que cumplió, o dice que sostenga el ritmo")
    void elFoco() {
        assertThat(html(DatosDePruebaDelDocumento.aprendiz())).contains("Foco para la próxima semana").contains("Emociones")
                .contains("2 de 4");

        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        DatosDelAprendiz completa = new DatosDelAprendiz(base.encabezado(), base.titular(), base.cifras(), base.tabla(), base.evidencias(),
                base.palabras(), base.evaluacionDelMentor(), new DatosDelAprendiz.FocoDeLaSemana(null, 0, 0), base.detalle());
        assertThat(html(completa)).contains("Sostener el ritmo");
    }

    @Test
    @DisplayName("spec 056 FR-035: el detalle día por día va en una segunda página, con cada hábito, su estado y por qué un día no se midió")
    void elDetalleEnLaSegundaPagina() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).contains("page-break-before: always").contains("class=\"segunda-pagina\" data-seccion=\"detalle\"")
                .contains("Detalle día por día").contains("Caminar 30 minutos", "Cumplido", "Sin nada planificado")
                .contains("sábado 14 de noviembre");
    }

    @Test
    @DisplayName("spec 056 FR-038: una semana sin ningún hábito programado dice «Sin actividad esta semana» y no trae tablas ni secciones vacías")
    void laSemanaSinActividad() {
        String html = html(DatosDePruebaDelDocumento.aprendizSinActividad());

        assertThat(html).contains("Sin actividad esta semana").doesNotContain("<table");
        assertThat(SeccionesDelHtml.de(html)).containsExactly("encabezado", "titular", "sin-actividad");
    }

    @Test
    @DisplayName("spec 056 FR-038: una semana con hábitos programados pero ninguno cumplido NO es una semana sin datos: trae su tabla")
    void unaSemanaDeCeroCumplidosTieneSusTablas() {
        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        DatosDelAprendiz datos = new DatosDelAprendiz(base.encabezado(), new Titular(ColorDeLaFila.ROJO, java.math.BigDecimal.ZERO),
                new Cifras(new Cifra(0, 3), new Cifra(0, 2), new Cifra(0, 1), new Cifra(5, 5)), base.tabla(), List.of(), List.of(),
                base.evaluacionDelMentor(), base.foco(), base.detalle());

        assertThat(html(datos)).doesNotContain("Sin actividad esta semana").contains("<table");
    }

    @Test
    @DisplayName("spec 056 FR-035: todo texto de una persona se escapa: nombre, mentor, grupo, hábito, lo que escribió y el motivo; nada ejecutable llega al HTML")
    void todoSeEscapa() {
        String malo = "<script>alert(1)</script>\"&";
        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz(malo);
        Encabezado e = base.encabezado();
        DatosDelAprendiz datos = new DatosDelAprendiz(new Encabezado(malo, malo, 47, 365, malo, malo, SABADO, VIERNES, ZONA), base.titular(),
                base.cifras(), base.tabla(), List.of(new EvidenciaDeLaSemana("FOTO", LocalDate.of(2026, 11, 14), "VALIDA", malo)),
                List.of(new PalabraDeLaPersona(LocalDate.of(2026, 11, 16), malo, malo)), base.evaluacionDelMentor(),
                new DatosDelAprendiz.FocoDeLaSemana(malo, 1, 2),
                List.of(new DetalleDelDia(SABADO, 46, null, List.of(new HabitoDelDia(malo, malo, "COMPLETADO", "ENTREGADA")))));

        String html = html(datos);

        assertThat(html).doesNotContain("<script").contains("&lt;script&gt;alert(1)&lt;/script&gt;&quot;&amp;");
        assertThat(e.nombre()).isEqualTo(malo);
    }

    @Test
    @DisplayName("spec 056 FR-037: el documento no contiene scripts ni recursos de afuera: es una página sola, A4 y lista para imprimir")
    void esUnaPaginaSolaParaImprimir() {
        String html = html(DatosDePruebaDelDocumento.aprendiz());

        assertThat(html).startsWith("<!doctype html>").contains("<html lang=\"es\"").contains("charset=\"utf-8\"")
                .contains("size: A4").contains("@media print")
                .doesNotContain("<script").doesNotContain("http://").doesNotContain("https://").doesNotContain("<link");
    }

    @Test
    @DisplayName("spec 056 FR-035: los números llevan el punto decimal del español de Perú y nunca la palabra null")
    void sinNullsEnElTexto() {
        assertThat(html(DatosDePruebaDelDocumento.aprendiz())).doesNotContain("null");
        assertThat(html(DatosDePruebaDelDocumento.aprendizSinActividad())).doesNotContain("null");
    }

    private static DatosDelAprendiz conEstado(ColorDeLaFila estado, String porcentaje) {
        DatosDelAprendiz base = DatosDePruebaDelDocumento.aprendiz();
        return new DatosDelAprendiz(base.encabezado(), new Titular(estado, porcentaje == null ? null : new java.math.BigDecimal(porcentaje)),
                base.cifras(), base.tabla(), base.evidencias(), base.palabras(), base.evaluacionDelMentor(), base.foco(), base.detalle());
    }
}
