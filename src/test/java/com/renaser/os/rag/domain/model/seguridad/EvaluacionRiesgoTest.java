package com.renaser.os.rag.domain.model.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La regla de monotonia de la compuerta de seguridad, probada sin Spring y sin base de datos.
 *
 * <p>Estas pruebas son el motivo por el que la regla vive en el dominio: una precedencia
 * declarada en un prompt no se puede probar, esta si.
 */
class EvaluacionRiesgoTest {

    private static EvaluacionRiesgo de(NivelRiesgo riesgo, Severidad severidad) {
        return EvaluacionRiesgo.de(riesgo, severidad);
    }

    // ---- construccion ----

    @Test
    @DisplayName("sinSenales arranca sin riesgo y con severidad baja")
    void sinSenalesArrancaLimpio() {
        assertThat(EvaluacionRiesgo.sinSenales())
                .isEqualTo(de(NivelRiesgo.NINGUNO, Severidad.BAJA));
    }

    @Test
    @DisplayName("rechaza un nivel de riesgo null")
    void rechazaRiesgoNull() {
        assertThatThrownBy(() -> de(null, Severidad.BAJA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rechaza una severidad null")
    void rechazaSeveridadNull() {
        assertThatThrownBy(() -> de(NivelRiesgo.NINGUNO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- monotonia: combinar solo sabe subir ----

    @Test
    @DisplayName("combinar sube el riesgo cuando la lectura nueva es mayor")
    void combinarSubeElRiesgo() {
        EvaluacionRiesgo previa = de(NivelRiesgo.NINGUNO, Severidad.BAJA);

        assertThat(previa.combinar(de(NivelRiesgo.CRITICO, Severidad.BAJA)).riesgo())
                .isEqualTo(NivelRiesgo.CRITICO);
    }

    @Test
    @DisplayName("combinar NO baja el riesgo aunque la lectura nueva sea menor")
    void combinarNoBajaElRiesgo() {
        EvaluacionRiesgo previa = de(NivelRiesgo.CRITICO, Severidad.BAJA);

        assertThat(previa.combinar(de(NivelRiesgo.NINGUNO, Severidad.BAJA)).riesgo())
                .isEqualTo(NivelRiesgo.CRITICO);
    }

    @Test
    @DisplayName("combinar sube la severidad cuando la lectura nueva es mayor")
    void combinarSubeLaSeveridad() {
        EvaluacionRiesgo previa = de(NivelRiesgo.NINGUNO, Severidad.BAJA);

        assertThat(previa.combinar(de(NivelRiesgo.NINGUNO, Severidad.ALTA)).severidad())
                .isEqualTo(Severidad.ALTA);
    }

    @Test
    @DisplayName("combinar NO baja la severidad aunque la lectura nueva sea menor")
    void combinarNoBajaLaSeveridad() {
        EvaluacionRiesgo previa = de(NivelRiesgo.NINGUNO, Severidad.ALTA);

        assertThat(previa.combinar(de(NivelRiesgo.NINGUNO, Severidad.BAJA)).severidad())
                .isEqualTo(Severidad.ALTA);
    }

    @Test
    @DisplayName("los dos ejes se combinan de forma independiente")
    void ejesIndependientes() {
        EvaluacionRiesgo previa = de(NivelRiesgo.ELEVADO, Severidad.BAJA);

        EvaluacionRiesgo resultado = previa.combinar(de(NivelRiesgo.NINGUNO, Severidad.ALTA));

        assertThat(resultado).isEqualTo(de(NivelRiesgo.ELEVADO, Severidad.ALTA));
    }

    @Test
    @DisplayName("combinar rechaza una evaluacion nueva null")
    void combinarRechazaNull() {
        assertThatThrownBy(() -> EvaluacionRiesgo.sinSenales().combinar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- la unica puerta para bajar ----

    @Test
    @DisplayName("rebajar sin motivo es rechazado")
    void rebajarSinMotivoRechazado() {
        EvaluacionRiesgo previa = de(NivelRiesgo.CRITICO, Severidad.ALTA);

        assertThatThrownBy(() -> previa.rebajarCon(EvaluacionRiesgo.sinSenales(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rebajar con un motivo en blanco es rechazado")
    void rebajarConMotivoEnBlancoRechazado() {
        EvaluacionRiesgo previa = de(NivelRiesgo.CRITICO, Severidad.ALTA);

        assertThatThrownBy(() -> previa.rebajarCon(EvaluacionRiesgo.sinSenales(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rebajar con motivo explicito si baja el nivel")
    void rebajarConMotivoBaja() {
        EvaluacionRiesgo previa = de(NivelRiesgo.CRITICO, Severidad.ALTA);

        EvaluacionRiesgo resultado = previa.rebajarCon(
                EvaluacionRiesgo.sinSenales(), "la persona aclaro que citaba la letra de una cancion");

        assertThat(resultado).isEqualTo(EvaluacionRiesgo.sinSenales());
    }

    @Test
    @DisplayName("rebajar rechaza una evaluacion nueva null")
    void rebajarRechazaNull() {
        assertThatThrownBy(() -> EvaluacionRiesgo.sinSenales().rebajarCon(null, "motivo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- consultas ----

    @Test
    @DisplayName("es critico solo cuando el riesgo es CRITICO, sin importar la severidad")
    void esCriticoSoloPorRiesgo() {
        assertThat(de(NivelRiesgo.CRITICO, Severidad.BAJA).esCritico()).isTrue();
        assertThat(de(NivelRiesgo.ELEVADO, Severidad.ALTA).esCritico()).isFalse();
    }

    @Test
    @DisplayName("requiere atencion si cualquiera de los dos ejes esta por encima del minimo")
    void requiereAtencionPorCualquierEje() {
        assertThat(EvaluacionRiesgo.sinSenales().requiereAtencion()).isFalse();
        assertThat(de(NivelRiesgo.NINGUNO, Severidad.ALTA).requiereAtencion()).isTrue();
        assertThat(de(NivelRiesgo.ELEVADO, Severidad.BAJA).requiereAtencion()).isTrue();
    }
}
