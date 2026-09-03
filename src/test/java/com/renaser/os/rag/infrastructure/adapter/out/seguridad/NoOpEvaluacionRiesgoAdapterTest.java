package com.renaser.os.rag.infrastructure.adapter.out.seguridad;

import com.renaser.os.rag.domain.model.seguridad.EvaluacionRiesgo;
import com.renaser.os.rag.domain.model.seguridad.NivelRiesgo;
import com.renaser.os.rag.domain.model.seguridad.Severidad;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpEvaluacionRiesgoAdapterTest {

    @Test
    void nuncaDevuelveNullYSiempreDevuelveSinSenales() {
        EvaluacionRiesgo resultado = new NoOpEvaluacionRiesgoAdapter().evaluar("cualquier mensaje");

        assertThat(resultado).isNotNull();
        assertThat(resultado.riesgo()).isEqualTo(NivelRiesgo.NINGUNO);
        assertThat(resultado.severidad()).isEqualTo(Severidad.BAJA);
    }

    @Test
    void esConsistenteSinImportarElContenidoDelMensaje() {
        NoOpEvaluacionRiesgoAdapter adapter = new NoOpEvaluacionRiesgoAdapter();

        assertThat(adapter.evaluar("")).isEqualTo(EvaluacionRiesgo.sinSenales());
        assertThat(adapter.evaluar("ayuda")).isEqualTo(EvaluacionRiesgo.sinSenales());
        assertThat(adapter.evaluar(null)).isEqualTo(EvaluacionRiesgo.sinSenales());
    }
}
