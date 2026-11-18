package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizEnAlerta;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.ConteoPorColor;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Foco;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.LoQueLeDijoElLider;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Pendientes;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.TitularDelGrupo;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes;
import com.renaser.os.leadership.domain.model.documento.DatosDelMes.MentorDelRanking;
import com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.SABADO;
import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.VIERNES;
import static com.renaser.os.leadership.domain.model.documento.DatosDePruebaDelDocumento.ZONA;
import static org.assertj.core.api.Assertions.assertThat;

/** El HTML del documento semanal del mentor (FR-036) y del reporte del mes (FR-032): sus secciones y lo que nunca lleva. */
class PlantillaDelDocumentoDelMentorYDelMesTest {

    private static final Instant GENERADO = Instant.parse("2026-11-18T03:00:00Z");

    private final PlantillaDelDocumento plantilla = new PlantillaDelDocumento();

    private String html(DatosDelMentor datos) {
        return plantilla.renderizar(datos, GENERADO);
    }

    @Test
    @DisplayName("spec 056 FR-036: trae las secciones del mentor en el orden de la spec: encabezado, titular, conteo, aprendices, pendientes, alertas, lo que le dijo el líder y foco")
    void lasSeccionesEnOrden() {
        assertThat(SeccionesDelHtml.de(html(DatosDePruebaDelDocumento.mentor()))).containsExactly("encabezado", "titular", "conteo",
                "aprendices", "pendientes", "alertas", "observaciones", "foco");
    }

    @Test
    @DisplayName("spec 056 FR-036: si el mentor sigue su programa suma su propia semana como la de un aprendiz, al final y con sus propias secciones")
    void suPropiaSemana() {
        String html = html(DatosDePruebaDelDocumento.mentor(DatosDePruebaDelDocumento.aprendiz("Luisa Rojas")));

        assertThat(SeccionesDelHtml.de(html)).containsExactly("encabezado", "titular", "conteo", "aprendices", "pendientes", "alertas",
                "observaciones", "foco", "su-semana", "su-semana.titular", "su-semana.cifras", "su-semana.tabla-de-pilares",
                "su-semana.evidencias", "su-semana.en-sus-palabras", "su-semana.evaluacion-del-mentor", "su-semana.foco", "su-semana.detalle");
        assertThat(html).contains("Su propia semana");
    }

    @Test
    @DisplayName("spec 056 FR-036: el encabezado trae el nombre, la formación, la semana y la fecha de generación en la zona del mentor")
    void elEncabezado() {
        assertThat(html(DatosDePruebaDelDocumento.mentor())).contains("Luisa Rojas", "Escuela Renaser", "Mentor")
                .contains("14 de noviembre de 2026", "20 de noviembre de 2026").contains("17 de noviembre de 2026, 22:00");
    }

    @Test
    @DisplayName("spec 056 FR-036: el titular de su grupo dice el estado con palabra y símbolo, el promedio y los grupos; el conteo, los cuatro colores con su forma")
    void elTitularYElConteo() {
        String html = html(DatosDePruebaDelDocumento.mentor());

        assertThat(html).contains("Requiere atención", "71.5 %", "Fénix").contains("4 aprendices");
        assertThat(html).contains("En rojo", "En ámbar", "Al día", "Sin datos").contains("×", "!", "✓", "·");
    }

    @Test
    @DisplayName("spec 056 FR-036: los aprendices salen en el orden recibido —por riesgo— con su estado en palabras y su tendencia en palabras y símbolo")
    void losAprendicesConSuTendencia() {
        String html = html(DatosDePruebaDelDocumento.mentor());

        assertThat(html).contains("Baja ↓", "Sube ↑", "Igual =", "Sin comparación");
        assertThat(html.indexOf("Ana Pérez")).isLessThan(html.indexOf("Beto Álvarez"));
        assertThat(html.indexOf("Beto Álvarez")).isLessThan(html.indexOf("Carla Ríos"));
        assertThat(html.indexOf("Carla Ríos")).isLessThan(html.indexOf("Diego Soto"));
        assertThat(html).contains("40 %", "95 %");
    }

    @Test
    @DisplayName("spec 056 FR-036: los pendientes dicen las consultas con los días de la más vieja, la evaluación del mes y a quién escribirle por estar en rojo")
    void losPendientes() {
        String html = html(DatosDePruebaDelDocumento.mentor());

        assertThat(html).contains("2 consultas sin responder", "5 días").contains("Evaluación del mes", "82.5 %")
                .contains("Escribir a los aprendices en rojo");
    }

    @Test
    @DisplayName("spec 056 FR-036: una fuente que no respondió se dice «no disponible» y nunca como cero ni como «null»")
    void unaFuenteCaida() {
        DatosDelMentor base = DatosDePruebaDelDocumento.mentor();
        DatosDelMentor datos = new DatosDelMentor(base.encabezado(), base.titular(), base.conteo(), base.aprendices(),
                new Pendientes(null, null, null, null, List.of()), base.alertas(), base.observaciones(), base.foco(), null);

        assertThat(html(datos)).contains("No disponible").doesNotContain("null").doesNotContain("0 consultas");
    }

    @Test
    @DisplayName("spec 056 FR-036: las alertas nombran a los aprendices con 3 o más días sin actividad; sin ninguna, lo dicen")
    void lasAlertas() {
        assertThat(html(DatosDePruebaDelDocumento.mentor())).contains("Ana Pérez", "3 días sin actividad");

        DatosDelMentor base = DatosDePruebaDelDocumento.mentor();
        DatosDelMentor sinAlertas = new DatosDelMentor(base.encabezado(), base.titular(), base.conteo(), base.aprendices(), base.pendientes(),
                List.of(), base.observaciones(), base.foco(), null);
        assertThat(html(sinAlertas)).contains("Sin alertas esta semana");
    }

    @Test
    @DisplayName("spec 056 FR-036: «lo que le dijo el líder» trae el tipo, el autor, el día y el texto, con la hora en la zona del mentor; sin nada, lo dice")
    void loQueLeDijoElLider() {
        assertThat(html(DatosDePruebaDelDocumento.mentor())).contains("Sugerencia", "Líder Uno", "Escríbele a Ana esta semana.")
                .contains("17 nov");

        DatosDelMentor base = DatosDePruebaDelDocumento.mentor();
        DatosDelMentor sinNada = new DatosDelMentor(base.encabezado(), base.titular(), base.conteo(), base.aprendices(), base.pendientes(),
                base.alertas(), List.of(), base.foco(), null);
        assertThat(html(sinNada)).contains("Nada esta semana");
    }

    @Test
    @DisplayName("spec 056 FR-036: el foco de la semana dice qué hacer según lo más urgente y, si no hay nada, que mantenga el ritmo")
    void elFoco() {
        assertThat(html(DatosDePruebaDelDocumento.mentor())).contains("Foco de la semana").contains("Escribir a 1 aprendiz en rojo");
        DatosDelMentor base = DatosDePruebaDelDocumento.mentor();
        assertThat(html(conFoco(base, new Foco("RESPONDER_CONSULTAS", 2)))).contains("Responder 2 consultas");
        assertThat(html(conFoco(base, new Foco("REVISAR_ALERTAS", 3)))).contains("Revisar las alertas");
        assertThat(html(conFoco(base, new Foco("MANTENER_EL_RITMO", 0)))).contains("Mantener el ritmo");
    }

    @Test
    @DisplayName("spec 056 FR-038: un mentor sin ningún grupo lo dice y no trae conteos ni tablas de aprendices vacías")
    void unMentorSinGrupo() {
        DatosDelMentor base = DatosDePruebaDelDocumento.mentor();
        DatosDelMentor datos = new DatosDelMentor(new DatosDelMentor.Encabezado("Luisa Rojas", "Escuela Renaser", 0, SABADO, VIERNES, ZONA),
                new TitularDelGrupo(ColorDeLaFila.SIN_DATOS, null, 0, List.of()), new ConteoPorColor(0, 0, 0, 0), List.of(),
                new Pendientes(0, null, "SIN_HISTORIAL", null, List.of()), List.of(), base.observaciones(),
                new Foco("MANTENER_EL_RITMO", 0), null);

        String html = html(datos);

        assertThat(html).contains("Sin grupo asignado").doesNotContain("<table");
        assertThat(SeccionesDelHtml.de(html)).containsExactly("encabezado", "titular", "observaciones");
    }

    @Test
    @DisplayName("spec 056 FR-036: todo texto de personas se escapa: el nombre del mentor y de sus aprendices, los grupos, lo que el líder escribió y su autor")
    void todoSeEscapa() {
        String malo = "<img src=x onerror=alert(1)>\"";
        DatosDelMentor datos = new DatosDelMentor(new DatosDelMentor.Encabezado(malo, malo, 1, SABADO, VIERNES, ZONA),
                new TitularDelGrupo(ColorDeLaFila.ROJO, BigDecimal.TEN, 1, List.of(malo)), new ConteoPorColor(1, 0, 0, 0),
                List.of(new AprendizDelMentor(malo, malo, ColorDeLaFila.ROJO, BigDecimal.TEN, "BAJA")),
                new Pendientes(1, 1, "CALCULADA", BigDecimal.TEN, List.of(malo)), List.of(new AprendizEnAlerta(malo, 4)),
                List.of(new LoQueLeDijoElLider("ALERTA", Instant.parse("2026-11-17T15:00:00Z"), malo, "</td><script>alert(1)</script>")),
                new Foco("ESCRIBIR_A_LOS_ROJOS", 1), null);

        String html = html(datos);

        assertThat(html).doesNotContain("<img src=x").doesNotContain("<script").doesNotContain("</td><script>")
                .contains("&lt;img src=x onerror=alert(1)&gt;&quot;");
    }

    @Test
    @DisplayName("spec 056 FR-032: el reporte del mes trae el promedio, cuántos están debajo del 60 %, el ranking con su estado y sus consultas respondidas, y los que no tienen evaluación")
    void elReporteDelMes() {
        String html = plantilla.renderizar(DatosDePruebaDelDocumento.mes(), GENERADO);

        assertThat(SeccionesDelHtml.de(html)).containsExactly("encabezado", "promedio", "ranking", "sin-muestra");
        assertThat(html).contains("noviembre de 2026", "72.4 %").contains("Debajo del 60 %").contains("Luisa Rojas", "84.2 %", "Marco Díaz", "55 %")
                .contains("Al día", "Con problemas").contains("Nora Sin Muestra").contains("Sin evaluación calculada");
    }

    @Test
    @DisplayName("spec 056 FR-032: un mes sin mentores evaluados lo dice y no deja una tabla vacía; el texto del mes se escapa")
    void unMesSinNadie() {
        DatosDelMes vacio = new DatosDelMes("2026-11", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ZONA, null, 0, List.of(), List.of());
        DatosDelMes malo = new DatosDelMes("2026-11", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ZONA, BigDecimal.TEN, 0,
                List.of(new MentorDelRanking("<script>x</script>", BigDecimal.TEN, null, 0)), List.of());

        assertThat(plantilla.renderizar(vacio, GENERADO)).contains("Sin mentores evaluados este mes").doesNotContain("<table").doesNotContain("null");
        assertThat(plantilla.renderizar(malo, GENERADO)).doesNotContain("<script>x").contains("&lt;script&gt;x&lt;/script&gt;").doesNotContain("null");
    }

    private static DatosDelMentor conFoco(DatosDelMentor base, Foco foco) {
        return new DatosDelMentor(base.encabezado(), base.titular(), base.conteo(), base.aprendices(), base.pendientes(), base.alertas(),
                base.observaciones(), foco, null);
    }
}
