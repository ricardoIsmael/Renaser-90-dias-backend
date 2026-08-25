package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.community.domain.model.publicacion.ReaccionMuro.Quitar;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro.Reaccionar;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro.ResultadoToggle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** wall/service.ts:422-432: tocar el mismo tipo lo saca, tocar el otro lo reemplaza — a lo
 * sumo una reaccion por usuario. */
class ReaccionMuroTest {

    @Test
    void sinReaccionPreviaReacciona() {
        ResultadoToggle resultado = ReaccionMuro.calcularToggle(null, TipoReaccion.ME_GUSTA);
        assertThat(resultado).isInstanceOf(Reaccionar.class);
        assertThat(((Reaccionar) resultado).tipo()).isEqualTo(TipoReaccion.ME_GUSTA);
    }

    @Test
    void tocarElMismoTipoLoQuita() {
        ResultadoToggle resultado = ReaccionMuro.calcularToggle(TipoReaccion.ME_GUSTA, TipoReaccion.ME_GUSTA);
        assertThat(resultado).isInstanceOf(Quitar.class);
    }

    @Test
    void tocarElOtroTipoLoReemplaza() {
        ResultadoToggle resultado = ReaccionMuro.calcularToggle(TipoReaccion.ME_GUSTA, TipoReaccion.NO_ME_GUSTA);
        assertThat(resultado).isInstanceOf(Reaccionar.class);
        assertThat(((Reaccionar) resultado).tipo()).isEqualTo(TipoReaccion.NO_ME_GUSTA);
    }

    @Test
    void tipoSolicitadoNuloEsInvalido() {
        assertThatThrownBy(() -> ReaccionMuro.calcularToggle(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
