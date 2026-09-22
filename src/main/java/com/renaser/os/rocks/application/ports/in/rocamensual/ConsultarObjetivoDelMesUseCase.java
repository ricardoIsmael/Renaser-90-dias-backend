package com.renaser.os.rocks.application.ports.in.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDeLaSemana;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * El plan mensual de los tres ejes, <b>calculado</b>: que tiene que estar logrado al cierre de cada
 * mes, y cual de los tres esta en curso.
 *
 * <p>Es distinto de {@code ConsultarRocasMensualesUseCase}, que devuelve lo <b>guardado</b> y nada
 * mas. Aca se devuelven las dos cosas juntas por el motivo de siempre: quien pinta la pantalla
 * necesita saber si el numero que muestra lo escribio la persona o lo calculo el sistema, y
 * resolverlo con dos llamadas obliga al cliente a cruzarlas por (eje, mes).
 */
public interface ConsultarObjetivoDelMesUseCase {

    /** Un {@link PlanMensualDelEje} por eje con Roca Maestra definida. Sin maestra no hay que repartir. */
    List<PlanMensualDelEje> misObjetivosMensuales(UserId actorId);

    /**
     * @param mesActual el mes que la persona transita hoy (1 a 3), derivado de su dia de programa.
     * @param semana    el tramo de la semana en curso, derivado del mes. {@code null} cuando el mes no
     *                  lleva cifra — y entonces la semana tampoco, por el mismo motivo.
     * @param unidad    tal como la escribio en el Mapa: {@code kg}, {@code cm}, {@code S/}. En
     *                  Relaciones es {@code /10}, que es la escala con la que el Mapa ya dibuja sus
     *                  hitos.
     * @param adelante  la moneda va delante del numero ({@code S/ 15 000}) y la unidad fisica
     *                  detras ({@code 75 kg}), igual que en los hitos del Mapa.
     */
    record PlanMensualDelEje(EjeObjetivo eje, int mesActual, String unidad, boolean adelante,
                              List<MesDelPlan> meses, ObjetivoDeLaSemana semana) {
    }

    /**
     * @param calculado lo que el sistema propone para ese mes. Nunca {@code null}: si no hay cifra,
     *                  trae el motivo.
     * @param editado   lo que la persona guardo para ese mes, o {@code null} si nunca lo toco. Cuando
     *                  existe, <b>manda</b>: el calculo pasa a ser la sugerencia contra la que se
     *                  comparo.
     */
    record MesDelPlan(int numeroMes, int diaDeCierre, boolean enCurso, ObjetivoDelMes calculado,
                       RocaMensual editado) {

        /** {@code true} cuando el numero que se muestra lo escribio la persona, no el sistema. */
        public boolean fueEditado() {
            return editado != null;
        }
    }
}
