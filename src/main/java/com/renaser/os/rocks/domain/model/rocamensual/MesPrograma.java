package com.renaser.os.rocks.domain.model.rocamensual;

/**
 * Los meses del programa: tres tramos de 30 dias. Mes 1 cierra al dia 30, mes 2 al 60 y mes 3 al
 * 90, tal como lo definio el cliente ("tres objetivos maestros, para 30/60/90 dias").
 *
 * <p><b>Por que 30 dias y no 4 semanas.</b> Son cosas distintas y conviene no confundirlas: la
 * semana de programa la calcula {@code SemanaPrograma} de lunes a domingo, y cuatro de esas dan 28
 * dias, no 30. Si el mes se definiera como "cuatro semanas", el mes 3 terminaria el dia 84 y
 * quedarian seis dias del programa fuera de todo mes. Contando de a 30 dias los tres meses cubren
 * los 90 exactos y ninguno queda huerfano. La consecuencia es que el corte mensual no cae siempre
 * en domingo, y eso esta bien: el mes es un hito del plan, no una semana calendario.
 *
 * <p>Todo aca es aritmetica sobre el dia de programa, sin fechas ni zonas horarias, que es lo que
 * evita la clase de error registrada como E-91 en la bitacora.
 */
public final class MesPrograma {

    /** Tres meses: 30, 60 y 90 dias. */
    public static final int MESES = 3;

    /** Dias que dura cada mes del programa. */
    public static final int DIAS_POR_MES = 30;

    /** Ultimo dia del programa. Coincide con el cierre del mes 3. */
    public static final int ULTIMO_DIA = MESES * DIAS_POR_MES;

    private MesPrograma() {
    }

    /**
     * Mes (1 a 3) al que pertenece un dia de programa.
     *
     * <p>El dia 0 —quien todavia no arranco— cuenta como mes 1: ya esta mirando su primer tramo, y
     * "mes 0" no significaria nada. Pasado el dia 90 se queda en 3.
     */
    public static int deDia(int diaPrograma) {
        int dia = Math.max(diaPrograma, 1);
        return Math.min((dia - 1) / DIAS_POR_MES + 1, MESES);
    }

    /** Ultimo dia de programa de ese mes: 30, 60 o 90. */
    public static int ultimoDiaDe(int numeroMes) {
        return acotar(numeroMes) * DIAS_POR_MES;
    }

    /** Primer dia de programa de ese mes: 1, 31 o 61. */
    public static int primerDiaDe(int numeroMes) {
        return (acotar(numeroMes) - 1) * DIAS_POR_MES + 1;
    }

    /** Dias que dura una semana. Aca es aritmetica sobre el dia de programa, no calendario. */
    public static final int DIAS_POR_SEMANA = 7;

    /**
     * Que dia de ESE mes es (1 a 30) para un dia de programa. El dia 31 es el dia 1 del mes 2.
     */
    public static int diaDentroDelMes(int diaPrograma) {
        int dia = Math.max(diaPrograma, 1);
        return (dia - 1) % DIAS_POR_MES + 1;
    }

    /**
     * Cuantas semanas quedan del mes, contando la que se esta transitando. De 1 a 5.
     *
     * <p><b>Ojo: no usa {@code SemanaPrograma} y es a proposito.</b> Aquella cuenta semanas
     * calendario de lunes a domingo, y cuatro de esas dan 28 dias, no 30 — mezclarlas dejaria dias
     * del mes fuera de toda semana, que es el mismo error que el javadoc de esta clase explica para
     * los meses. Aca la semana es un bloque de siete dias de programa dentro del mes, que es lo que
     * necesita repartir una cifra mensual: la cuenta cierra exacta y no depende de en que dia de la
     * semana arranco la persona.
     *
     * <p>La consecuencia asumida: un mes de 30 dias da 5 bloques y el ultimo es de dos dias. Es
     * correcto — al cierre del mes hay que estar en la cifra del mes, sobre el bloque corto o no.
     */
    public static int semanasQueQuedanDelMes(int diaPrograma) {
        int diasQueQuedan = DIAS_POR_MES - diaDentroDelMes(diaPrograma) + 1;
        return Math.max((diasQueQuedan + DIAS_POR_SEMANA - 1) / DIAS_POR_SEMANA, 1);
    }

    /** {@code true} si el numero cae dentro de los tres meses del programa. */
    public static boolean esValido(int numeroMes) {
        return numeroMes >= 1 && numeroMes <= MESES;
    }

    private static int acotar(int numeroMes) {
        return Math.min(Math.max(numeroMes, 1), MESES);
    }
}
