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

    // ------------------------------------------------------------------------------------------------ la meta medible (ficha con diagnóstico)

    private final FichaDelDiagnostico fichaConMetas = Fichas.ficha(
            Fichas.alimenta(1, DestinoDeRespuesta.OBJETIVO_CUERPO, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(2, DestinoDeRespuesta.UNIDAD_CUERPO, TipoPreguntaOnboarding.TEXTO, List.of()),
            Fichas.alimenta(3, DestinoDeRespuesta.PARTIDA_CUERPO, TipoPreguntaOnboarding.NUMERO, List.of()),
            Fichas.alimenta(4, DestinoDeRespuesta.META_CUERPO, TipoPreguntaOnboarding.NUMERO, List.of()),
            Fichas.alimenta(5, DestinoDeRespuesta.OBJETIVO_RELACIONES, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(6, DestinoDeRespuesta.PARTIDA_RELACIONES, TipoPreguntaOnboarding.NUMERO, List.of()),
            Fichas.alimenta(7, DestinoDeRespuesta.META_RELACIONES, TipoPreguntaOnboarding.NUMERO, List.of()),
            Fichas.alimenta(8, DestinoDeRespuesta.OBJETIVO_NEGOCIO, TipoPreguntaOnboarding.AREA_TEXTO, List.of()),
            Fichas.alimenta(9, DestinoDeRespuesta.META_NEGOCIO, TipoPreguntaOnboarding.NUMERO, List.of()));

    private static ObjetivosDelDiagnostico.ObjetivoPorEje deEje(ObjetivosDelDiagnostico nacidos, EjeDelDiagnostico eje) {
        return nacidos.objetivos().stream().filter(o -> o.eje() == eje).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("la meta de Cuerpo trae a dónde llegar, desde dónde parte y la unidad que escribió")
    void metaDeCuerpo() {
        var respuestas = Fichas.por(Fichas.texto(1, "Bajar de peso"), Fichas.texto(2, " kg "), Fichas.numero(3, 92), Fichas.numero(4, 85));

        var meta = deEje(ObjetivosDelDiagnostico.de(fichaConMetas, respuestas), EjeDelDiagnostico.CUERPO).meta();

        assertThat(meta).isNotNull();
        assertThat(meta.unidad()).isEqualTo("kg");
        assertThat(meta.partida()).isEqualByComparingTo("92");
        assertThat(meta.meta()).isEqualByComparingTo("85");
    }

    @Test
    @DisplayName("Relaciones es siempre una escala del 1 al 10: no pregunta la unidad")
    void metaDeRelaciones() {
        var respuestas = Fichas.por(Fichas.texto(5, "Hablar sin evitar"), Fichas.numero(6, 4), Fichas.numero(7, 8));

        var meta = deEje(ObjetivosDelDiagnostico.de(fichaConMetas, respuestas), EjeDelDiagnostico.RELACIONES).meta();

        assertThat(meta).isNotNull();
        assertThat(meta.unidad()).isEqualTo(ObjetivosDelDiagnostico.UNIDAD_DE_LA_ESCALA);
        assertThat(meta.partida()).isEqualByComparingTo("4");
        assertThat(meta.meta()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("una meta a medias no es una meta: el objetivo queda en palabras")
    void metaIncompleta() {
        var sinUnidad = Fichas.por(Fichas.texto(1, "Bajar de peso"), Fichas.numero(3, 92), Fichas.numero(4, 85));
        var sinPartida = Fichas.por(Fichas.texto(1, "Bajar de peso"), Fichas.texto(2, "kg"), Fichas.numero(4, 85));
        var soloNegocioSinPartida = Fichas.por(Fichas.texto(8, "Facturar más"), Fichas.numero(9, 15000));

        assertThat(deEje(ObjetivosDelDiagnostico.de(fichaConMetas, sinUnidad), EjeDelDiagnostico.CUERPO).meta()).isNull();
        assertThat(deEje(ObjetivosDelDiagnostico.de(fichaConMetas, sinPartida), EjeDelDiagnostico.CUERPO).meta()).isNull();
        assertThat(deEje(ObjetivosDelDiagnostico.de(fichaConMetas, soloNegocioSinPartida), EjeDelDiagnostico.TRABAJO).meta()).isNull();
    }

    @Test
    @DisplayName("la meta sin el objetivo en palabras no crea ningún objetivo")
    void metaSinObjetivo() {
        var respuestas = Fichas.por(Fichas.texto(2, "kg"), Fichas.numero(3, 92), Fichas.numero(4, 85));

        assertThat(ObjetivosDelDiagnostico.de(fichaConMetas, respuestas).objetivos()).isEmpty();
    }

    @Test
    @DisplayName("las preguntas de la meta no cuentan como objetivo, prioridad ni acción")
    void metaNoEsObjetivo() {
        var respuestas = Fichas.por(Fichas.texto(1, "Bajar de peso"), Fichas.texto(2, "kg"), Fichas.numero(3, 92), Fichas.numero(4, 85));

        var nacidos = ObjetivosDelDiagnostico.de(fichaConMetas, respuestas);

        assertThat(nacidos.objetivos()).hasSize(1);
        assertThat(nacidos.prioridad()).isNull();
        assertThat(nacidos.primeraAccion()).isNull();
    }
}
