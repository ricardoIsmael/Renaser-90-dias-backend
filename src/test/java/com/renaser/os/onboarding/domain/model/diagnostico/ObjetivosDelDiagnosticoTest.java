package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObjetivosDelDiagnosticoTest {

    private static final List<OpcionDelDiagnostico> EJES = List.of(new OpcionDelDiagnostico("RELACIONES", "Relaciones."),
            new OpcionDelDiagnostico("CUERPO", "Salud."), new OpcionDelDiagnostico("TRABAJO", "Negocios."));

    private final FichaDelDiagnostico ficha = Fichas.ficha(
            Fichas.alimenta(1, DestinoDeRespuesta.OBJETIVO_RELACIONES, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(2, DestinoDeRespuesta.OBJETIVO_CUERPO, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(3, DestinoDeRespuesta.OBJETIVO_NEGOCIO, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(4, DestinoDeRespuesta.PRIORIDAD_PRINCIPAL, TipoPreguntaOnboarding.SELECCION_UNICA, EJES),
            Fichas.alimenta(5, DestinoDeRespuesta.PRIMERA_ACCION, TipoPreguntaOnboarding.TEXTO, List.of()));

    @Test
    @DisplayName("tres objetivos con el de la prioridad principal primero, y la primera acción")
    void objetivosEnOrden() {
        var respuestas = Fichas.por(Fichas.texto(1, "Hablar sin evitar"), Fichas.texto(2, "Entrenar 4 días"), Fichas.texto(3, "Facturar 15 mil"),
                Fichas.unica(4, "TRABAJO"), Fichas.texto(5, "El lunes reviso mis gastos"));

        ObjetivosDelDiagnostico nacidos = ObjetivosDelDiagnostico.de(ficha, respuestas);

        assertThat(nacidos.objetivos()).extracting(o -> o.eje()).containsExactly(EjeDelDiagnostico.TRABAJO, EjeDelDiagnostico.CUERPO, EjeDelDiagnostico.RELACIONES);
        assertThat(nacidos.objetivos().get(0).texto()).isEqualTo("Facturar 15 mil");
        assertThat(nacidos.prioridad()).isEqualTo(EjeDelDiagnostico.TRABAJO);
        assertThat(nacidos.primeraAccion()).isEqualTo("El lunes reviso mis gastos");
    }

    @Test
    @DisplayName("un eje sin respuesta no trae objetivo: se escribe en Plan")
    void ejeSinRespuesta() {
        var respuestas = Fichas.por(Fichas.texto(2, "Entrenar 4 días"), Fichas.unica(4, "CUERPO"));

        ObjetivosDelDiagnostico nacidos = ObjetivosDelDiagnostico.de(ficha, respuestas);

        assertThat(nacidos.objetivos()).extracting(o -> o.eje()).containsExactly(EjeDelDiagnostico.CUERPO);
        assertThat(nacidos.primeraAccion()).isNull();
    }

    @Test
    @DisplayName("sin prioridad, el orden es Cuerpo, Negocio, Relaciones")
    void sinPrioridad() {
        var respuestas = Fichas.por(Fichas.texto(1, "R"), Fichas.texto(3, "N"));
        assertThat(ObjetivosDelDiagnostico.de(ficha, respuestas).objetivos()).extracting(o -> o.eje())
                .containsExactly(EjeDelDiagnostico.TRABAJO, EjeDelDiagnostico.RELACIONES);
    }

    @Test
    @DisplayName("lo que alimenta pero está oculto por una condición no cuenta")
    void ocultoNoAlimenta() {
        PreguntaDelDiagnostico padre = Fichas.pregunta(10, TipoPreguntaOnboarding.SELECCION_UNICA, true);
        PreguntaDelDiagnostico hijaQueAlimenta = new PreguntaDelDiagnostico(11, "diag_p11", 1, 11, "Objetivo", null, TipoPreguntaOnboarding.AREA_TEXTO, true,
                null, List.of(), null, new CondicionDeVisibilidad(10, "Sí."), DestinoDeRespuesta.OBJETIVO_CUERPO, java.util.Set.of(), null, null);
        FichaDelDiagnostico f = Fichas.ficha(padre, hijaQueAlimenta);

        assertThat(ObjetivosDelDiagnostico.de(f, Fichas.por(Fichas.unica(10, "No."), Fichas.texto(11, "x"))).objetivos()).isEmpty();
        assertThat(ObjetivosDelDiagnostico.de(f, Fichas.por(Fichas.unica(10, "Sí."), Fichas.texto(11, "x"))).objetivos()).hasSize(1);
    }
}
