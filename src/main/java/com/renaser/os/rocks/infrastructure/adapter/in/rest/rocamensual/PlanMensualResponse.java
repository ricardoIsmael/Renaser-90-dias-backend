package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.MesDelPlan;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.PlanMensualDelEje;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDeLaSemana;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;

import java.math.BigDecimal;
import java.util.List;

/**
 * El plan mensual de un eje, tal como lo lee la app: los tres meses con su cifra, y de donde salio
 * cada una.
 *
 * @param unidadAdelante la moneda va delante del numero ({@code S/ 15 000}) y la unidad fisica
 *                       detras ({@code 75 kg}), igual que en los hitos del Mapa.
 */
public record PlanMensualResponse(String eje, int mesActual, String unidad, boolean unidadAdelante,
                                   List<MesResponse> meses, SemanaResponse semana) {

    public static PlanMensualResponse from(PlanMensualDelEje plan) {
        return new PlanMensualResponse(plan.eje().name(), plan.mesActual(), plan.unidad(), plan.adelante(),
                plan.meses().stream().map(MesResponse::from).toList(), SemanaResponse.from(plan.semana()));
    }

    /**
     * El tramo de la semana en curso. {@code null} cuando el mes no lleva cifra — y entonces la
     * semana tampoco, por el mismo motivo que ya explica el mes; no se repite el porque dos veces.
     *
     * @param cifra          donde hay que estar al cierre de ESTA semana.
     * @param paso           cuanto hay que moverse durante la semana. Siempre positivo.
     * @param semanasQueQuedan las que faltan del mes, contando la que se esta transitando (1 a 5).
     */
    public record SemanaResponse(BigDecimal cifra, BigDecimal paso, int semanasQueQuedan, boolean sube) {

        static SemanaResponse from(ObjetivoDeLaSemana semana) {
            return semana == null ? null
                    : new SemanaResponse(semana.valor(), semana.paso(), semana.semanasQueQuedan(), semana.sube());
        }
    }

    /**
     * Un mes del plan.
     *
     * @param estado {@code CON_CIFRA}, {@code YA_ALCANZADO}, {@code SIN_CIFRA} o {@code SOLO_TITULO}.
     *               Es lo que decide que pinta la pantalla, y existe para que el cliente no tenga que
     *               deducirlo de cuales campos vinieron en null.
     * @param cifra  donde hay que estar al cierre del mes. {@code null} cuando no hay cifra — y
     *               entonces {@code motivo} dice por que, que es la unica respuesta honesta.
     * @param paso   cuanto hay que moverse durante ese mes. Siempre positivo.
     * @param falta  lo que queda para la meta del dia 90. Siempre positivo.
     * @param sube   hacia donde viaja el objetivo. {@code null} cuando no hay cifra.
     * @param editado {@code true} = el numero lo escribio la persona y el calculo quedo de lado.
     */
    public record MesResponse(int numeroMes, int diaDeCierre, boolean enCurso, String estado, BigDecimal cifra,
                               BigDecimal paso, BigDecimal falta, Boolean sube, String motivo, boolean editado,
                               String titulo) {

        static MesResponse from(MesDelPlan mes) {
            return mes.fueEditado() ? deLoEditado(mes) : deLoCalculado(mes);
        }

        /**
         * Lo que la persona guardo manda sobre el calculo. No se devuelve ademas la cifra calculada:
         * dos numeros para el mismo mes en la misma respuesta es exactamente el problema que este
         * trabajo vino a cerrar.
         */
        private static MesResponse deLoEditado(MesDelPlan mes) {
            RocaMensual editado = mes.editado();
            BigDecimal cifra = editado.tieneMeta() ? editado.meta().objetivo() : null;
            return new MesResponse(mes.numeroMes(), mes.diaDeCierre(), mes.enCurso(),
                    cifra == null ? "SOLO_TITULO" : "CON_CIFRA", cifra, null, null, null, null, true,
                    editado.titulo());
        }

        private static MesResponse deLoCalculado(MesDelPlan mes) {
            return switch (mes.calculado()) {
                case ObjetivoDelMes.ConCifra c -> new MesResponse(mes.numeroMes(), mes.diaDeCierre(), mes.enCurso(),
                        "CON_CIFRA", c.cifraQueSeMuestra(), c.paso(), c.falta(), c.sube(), null, false, null);
                case ObjetivoDelMes.YaAlcanzado y -> new MesResponse(mes.numeroMes(), mes.diaDeCierre(),
                        mes.enCurso(), "YA_ALCANZADO", y.meta(), null, null, null, null, false, null);
                case ObjetivoDelMes.SinCifra s -> new MesResponse(mes.numeroMes(), mes.diaDeCierre(), mes.enCurso(),
                        "SIN_CIFRA", null, null, null, null, s.motivo().name(), false, null);
            };
        }
    }
}
