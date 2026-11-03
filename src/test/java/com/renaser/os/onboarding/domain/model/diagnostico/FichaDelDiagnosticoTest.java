package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FichaDelDiagnosticoTest {

    private final PreguntaDelDiagnostico tieneNegocio = Fichas.pregunta(1, TipoPreguntaOnboarding.SELECCION_UNICA, true);
    private final PreguntaDelDiagnostico aQueSeDedica = Fichas.conCondicion(2, 1, "Sí.");
    private final PreguntaDelDiagnostico cuantosTrabajan = Fichas.conCondicion(3, 2, "x");
    private final PreguntaDelDiagnostico suelta = Fichas.pregunta(4, TipoPreguntaOnboarding.TEXTO, true);

    @Test
    @DisplayName("una pregunta condicionada se muestra solo con la respuesta que la activa, y sus hijas dependen de ella")
    void visibles() {
        FichaDelDiagnostico ficha = Fichas.ficha(tieneNegocio, aQueSeDedica, suelta);
        assertThat(ficha.visibles(Fichas.por())).containsExactlyInAnyOrder(1, 4);
        assertThat(ficha.visibles(Fichas.por(Fichas.unica(1, "No.")))).containsExactlyInAnyOrder(1, 4);
        assertThat(ficha.visibles(Fichas.por(Fichas.unica(1, "Sí.")))).containsExactlyInAnyOrder(1, 2, 4);
    }

    @Test
    @DisplayName("faltan las obligatorias visibles; las ocultas por una condición no se piden")
    void faltantes() {
        FichaDelDiagnostico ficha = Fichas.ficha(tieneNegocio, aQueSeDedica, suelta);
        assertThat(ficha.faltantes(Fichas.por())).containsExactly(1, 4);
        assertThat(ficha.faltantes(Fichas.por(Fichas.unica(1, "No."), Fichas.texto(4, "x")))).isEmpty();
        assertThat(ficha.faltantes(Fichas.por(Fichas.unica(1, "Sí."), Fichas.texto(4, "x")))).containsExactly(2);
    }

    @Test
    @DisplayName("las respuestas de lo que dejó de mostrarse se conservan pero no cuentan")
    void respuestasDeLoOculto() {
        FichaDelDiagnostico ficha = Fichas.ficha(tieneNegocio, aQueSeDedica, suelta);
        var respuestas = Fichas.por(Fichas.unica(1, "No."), Fichas.texto(2, "Panadería"), Fichas.texto(4, "x"));
        assertThat(ficha.visibles(respuestas)).doesNotContain(2);
        assertThat(ficha.faltantes(respuestas)).isEmpty();
    }

    @Test
    @DisplayName("quitar una pregunta arrastra a las que dependen de ella, aunque sea en cadena")
    void dependientes() {
        FichaDelDiagnostico ficha = Fichas.ficha(tieneNegocio, aQueSeDedica, cuantosTrabajan, suelta);
        assertThat(ficha.dependientesDe(List.of(1))).containsExactlyInAnyOrder(2, 3);
        assertThat(ficha.dependientesDe(List.of(4))).isEmpty();
    }

    @Test
    @DisplayName("la condición tiene que apuntar a una pregunta anterior, de opciones, y a una de sus opciones")
    void errorDeLaCondicion() {
        assertThat(Fichas.ficha(tieneNegocio, aQueSeDedica).errorDeLaCondicion(aQueSeDedica)).isEmpty();
        assertThat(Fichas.ficha(aQueSeDedica, tieneNegocio).errorDeLaCondicion(aQueSeDedica).orElse("")).contains("anterior");
        assertThat(Fichas.ficha(suelta, Fichas.conCondicion(5, 4, "Sí.")).errorDeLaCondicion(Fichas.conCondicion(5, 4, "Sí.")).orElse(""))
                .contains("pregunta de opciones");
        PreguntaDelDiagnostico conOpcionQueNoEs = Fichas.conCondicion(6, 1, "Jamás");
        assertThat(Fichas.ficha(tieneNegocio, conOpcionQueNoEs).errorDeLaCondicion(conOpcionQueNoEs).orElse("")).contains("no es una opción");
        PreguntaDelDiagnostico huerfana = Fichas.conCondicion(7, 99, "Sí.");
        assertThat(Fichas.ficha(tieneNegocio, huerfana).errorDeLaCondicion(huerfana).orElse("")).contains("no existe");
    }

    @Test
    @DisplayName("no se publica sin preguntas, sin consentimiento o con la declaración de relleno; con el texto del dueño sí")
    void motivosParaNoPublicar() {
        assertThat(Fichas.ficha().motivosParaNoPublicar()).anyMatch(m -> m.contains("no tiene preguntas"));
        assertThat(Fichas.ficha(suelta).motivosParaNoPublicar()).anyMatch(m -> m.contains("consentimiento"));
        PreguntaDelDiagnostico conRelleno = consentimiento(FichaDelDiagnostico.MARCA_DE_DECLARACION_PENDIENTE + " texto provisional");
        assertThat(Fichas.ficha(suelta, conRelleno).motivosParaNoPublicar()).anyMatch(m -> m.contains("declaración"));
        PreguntaDelDiagnostico conTexto = consentimiento("Declaro que lo que respondo es verdadero y autorizo su uso para acompañarme.");
        assertThat(Fichas.ficha(suelta, conTexto).motivosParaNoPublicar()).isEmpty();
    }

    @Test
    @DisplayName("por formación: la pregunta del Diplomado no la ve la Escuela; Administración (null) ve todas; las secciones vacías se van, la portada no")
    void porFormacion() {
        PreguntaDelDiagnostico soloDiplomado = Fichas.paraFormaciones(8, Formacion.DIPLOMADO);
        FichaDelDiagnostico ficha = Fichas.ficha(suelta, soloDiplomado);
        assertThat(ficha.paraLaFormacion(Formacion.ESCUELA).preguntas()).extracting(PreguntaDelDiagnostico::id).containsExactly(4);
        assertThat(ficha.paraLaFormacion(Formacion.DIPLOMADO).preguntas()).extracting(PreguntaDelDiagnostico::id).containsExactly(4, 8);
        assertThat(ficha.paraLaFormacion(null).preguntas()).hasSize(2);
        FichaDelDiagnostico soloLaDelDiplomado = Fichas.ficha(soloDiplomado);
        assertThat(soloLaDelDiplomado.paraLaFormacion(Formacion.ESCUELA).secciones()).extracting(SeccionDelDiagnostico::esPortada).containsExactly(true);
    }

    @Test
    @DisplayName("el plazo y la formación se nombran en los textos de la persona")
    void textosParaLaPersona() {
        PreguntaDelDiagnostico p = PreguntaDelDiagnostico.nueva(1, 0, null, "¿Qué quieres conseguir en {duracion}?", "En {duracion}, en {formacion}.",
                TipoPreguntaOnboarding.TEXTO, true, null, List.of(), null, null, null, Set.of(), null);
        FichaDelDiagnostico lectura = Fichas.ficha(p).conTextosPara(TextoParaLaPersona.duracionEnPalabras(365), "Diplomado Renaser");
        assertThat(lectura.preguntas().get(0).texto()).isEqualTo("¿Qué quieres conseguir en 1 año?");
        assertThat(lectura.preguntas().get(0).ayuda()).isEqualTo("En 1 año, en Diplomado Renaser.");
        assertThat(lectura.portada().orElseThrow().titulo()).isEqualTo("Diagnóstico · Diplomado Renaser");
        assertThat(TextoParaLaPersona.duracionEnPalabras(90)).isEqualTo("90 días");
    }

    private static PreguntaDelDiagnostico consentimiento(String declaracion) {
        return new PreguntaDelDiagnostico(9, "diag_p23", 1, 9, "Declaración de responsabilidad y consentimiento", declaracion, TipoPreguntaOnboarding.CASILLA,
                true, null, Fichas.opcionesPara(TipoPreguntaOnboarding.CASILLA), null, null, null, Set.of(), null, null);
    }
}
