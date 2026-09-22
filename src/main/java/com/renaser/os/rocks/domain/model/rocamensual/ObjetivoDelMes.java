package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;

/**
 * Lo que hay que lograr al cierre de un mes, ya calculado. Tres variantes cerradas, y ninguna
 * miente: o hay una cifra, o ya se llego, o no hay cifra <b>y se dice por que</b>.
 *
 * <p>Cuando no hay cifra, el resultado trae el motivo y <b>nada mas</b>: el numero descartado no
 * viaja. Es deliberado — lo que no esta no se puede pintar por descuido, y el pedido del dueno fue
 * exactamente ese, que un "baja 20 kg este mes" no se muestre.
 */
public sealed interface ObjetivoDelMes {

    /** Mes del programa al que corresponde (1 a 3). */
    int numeroMes();

    /** Dia de programa en que cierra ese mes: 30, 60 o 90. */
    default int diaDeCierre() {
        return MesPrograma.ultimoDiaDe(numeroMes());
    }

    /**
     * Hay cifra.
     *
     * @param valor  donde hay que estar al cierre del mes (o, en una meta acumulada, el total
     *               corrido que deberia llevarse).
     * @param paso   cuanto hay que moverse durante el mes. Siempre positivo; la direccion va aparte.
     * @param falta  lo que queda para la meta del dia 90 desde el valor real de hoy. Siempre positivo.
     * @param sube   hacia donde viaja el objetivo. Sale de comparar la meta con la linea base.
     */
    record ConCifra(int numeroMes, Magnitud magnitud, BigDecimal valor, BigDecimal paso, BigDecimal falta,
                     boolean sube) implements ObjetivoDelMes {

        /**
         * En una meta acumulada el numero que importa es <b>el paso</b> ("S/ 4 500 este mes") y no
         * el nivel: el total corrido es contexto, no el objetivo del mes.
         */
        public BigDecimal cifraQueSeMuestra() {
            return magnitud == Magnitud.ACUMULADO ? paso : valor;
        }
    }

    /** Ya se llego a la meta del dia 90 (o se paso, en el sentido del viaje). El mes es sostenerla. */
    record YaAlcanzado(int numeroMes, BigDecimal meta) implements ObjetivoDelMes {
    }

    /** No hay cifra este mes, y el motivo explica cual es el campo que falta o la regla que aplica. */
    record SinCifra(int numeroMes, MotivoSinCifra motivo) implements ObjetivoDelMes {
    }

    /** Por que este mes no lleva cifra. Cada motivo tiene su propia explicacion en pantalla. */
    enum MotivoSinCifra {

        /** Falta la linea base o la meta del dia 90: no hay con que calcular. */
        SIN_DATOS,

        /** Todavia no se eligio que se mide (ni cada cuanto, en negocio): no hay que proyectar. */
        SIN_TIPO,

        /** Linea base y meta son el mismo numero: no hay distancia que repartir. */
        SIN_RECORRIDO,

        /** Condicion clinica: el ritmo lo define quien atiende a la persona, no esta app. */
        ACOMPANAMIENTO_CLINICO,

        /** El ritmo necesario supera todo el plan original. Tope relativo. */
        FUERA_DE_ALCANCE,

        /** El ritmo necesario supera lo que se puede cambiar con salud. Tope absoluto. */
        RITMO_NO_SALUDABLE
    }
}
