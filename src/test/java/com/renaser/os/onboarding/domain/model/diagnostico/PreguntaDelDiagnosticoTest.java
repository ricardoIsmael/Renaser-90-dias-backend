package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreguntaDelDiagnosticoTest {

    private static PreguntaDelDiagnostico nueva(TipoPreguntaOnboarding tipo, Integer maximo, List<OpcionDelDiagnostico> opciones, DestinoDeRespuesta destino) {
        return PreguntaDelDiagnostico.nueva(1, 0, null, "¿Algo?", null, tipo, true, maximo, opciones, null, null, destino, Set.of(), null);
    }

    @Test
    @DisplayName("una pregunta de opciones sin opciones no se puede crear")
    void opcionesObligatorias() {
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.SELECCION_UNICA, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos una opción");
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.CUADRICULA, null, List.of(), null))
                .hasMessageContaining("al menos una fila");
    }

    @Test
    @DisplayName("el máximo de las casillas va de 1 al número de opciones y solo las casillas lo tienen")
    void maximoDeOpciones() {
        List<OpcionDelDiagnostico> tres = Fichas.opcionesPara(TipoPreguntaOnboarding.SELECCION_MULTIPLE);
        assertThat(nueva(TipoPreguntaOnboarding.SELECCION_MULTIPLE, 2, tres, null).maximoOpciones()).isEqualTo(2);
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.SELECCION_MULTIPLE, 4, tres, null)).hasMessageContaining("1 a 3");
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.SELECCION_UNICA, 1, tres, null)).hasMessageContaining("Solo las casillas");
    }

    @Test
    @DisplayName("dos opciones iguales se rechazan")
    void opcionesRepetidas() {
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.SELECCION_UNICA, null,
                List.of(OpcionDelDiagnostico.de("Sí."), OpcionDelDiagnostico.de("Sí.")), null)).hasMessageContaining("repetida");
    }

    @Test
    @DisplayName("el objetivo se responde con texto y la prioridad lleva un eje en cada opción")
    void destinosYSusTipos() {
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.NUMERO, null, List.of(), DestinoDeRespuesta.OBJETIVO_CUERPO))
                .hasMessageContaining("se responde con texto");
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.SELECCION_UNICA, null,
                List.of(OpcionDelDiagnostico.de("Relaciones."), OpcionDelDiagnostico.de("Salud.")), DestinoDeRespuesta.PRIORIDAD_PRINCIPAL))
                .hasMessageContaining("tiene que decir a cuál corresponde");
        PreguntaDelDiagnostico prioridad = nueva(TipoPreguntaOnboarding.SELECCION_UNICA, null,
                List.of(new OpcionDelDiagnostico("RELACIONES", "Relaciones."), new OpcionDelDiagnostico("CUERPO", "Salud.")),
                DestinoDeRespuesta.PRIORIDAD_PRINCIPAL);
        assertThat(prioridad.alimenta()).isEqualTo(DestinoDeRespuesta.PRIORIDAD_PRINCIPAL);
    }

    @Test
    @DisplayName("la que alimenta un objetivo tiene tope de 500 caracteres; las demás, no")
    void largoMaximo() {
        assertThat(nueva(TipoPreguntaOnboarding.AREA_TEXTO, null, List.of(), DestinoDeRespuesta.OBJETIVO_NEGOCIO).largoMaximo()).isEqualTo(500);
        assertThat(nueva(TipoPreguntaOnboarding.AREA_TEXTO, null, List.of(), null).largoMaximo()).isNull();
    }

    @Test
    @DisplayName("protegidas: el trato y el consentimiento; las que alimentan un objetivo, no")
    void protegidas() {
        assertThat(nueva(TipoPreguntaOnboarding.SELECCION_UNICA, null,
                List.of(new OpcionDelDiagnostico("MASCULINO", "Él"), new OpcionDelDiagnostico("FEMENINO", "Ella")), DestinoDeRespuesta.TRATO_DE_LA_PERSONA)
                .esProtegida()).isTrue();
        assertThat(nueva(TipoPreguntaOnboarding.CASILLA, null, List.of(OpcionDelDiagnostico.de("Acepto")), null).esProtegida()).isTrue();
        assertThat(nueva(TipoPreguntaOnboarding.TEXTO, null, List.of(), DestinoDeRespuesta.OBJETIVO_CUERPO).esProtegida()).isFalse();
    }

    @Test
    @DisplayName("sin formaciones se muestra en todas; con formaciones, solo en ésas; quien ve todo (null) la ve siempre")
    void formaciones() {
        PreguntaDelDiagnostico solo = Fichas.paraFormaciones(1, Formacion.DIPLOMADO);
        assertThat(solo.seMuestraEn(Formacion.DIPLOMADO)).isTrue();
        assertThat(solo.seMuestraEn(Formacion.ESCUELA)).isFalse();
        assertThat(solo.seMuestraEn(null)).isTrue();
        assertThat(Fichas.paraFormaciones(2).seMuestraEn(Formacion.CERTIFICACION)).isTrue();
    }

    @Test
    @DisplayName("el tipo de audio, firma o archivo no existe en el diagnóstico")
    void tiposNoPermitidos() {
        assertThatThrownBy(() -> nueva(TipoPreguntaOnboarding.AUDIO, null, List.of(), null)).hasMessageContaining("no admite");
    }
}
