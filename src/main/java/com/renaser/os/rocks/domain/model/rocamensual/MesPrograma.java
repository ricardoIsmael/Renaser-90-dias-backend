package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.shared.domain.DuracionDelPrograma;

/**
 * Los meses del programa: tres tramos de 30 dias. Mes 1 cierra al dia 30, mes 2 al 60 y mes 3 al
 * 90, tal como lo definio el cliente ("tres objetivos maestros, para 30/60/90 dias").
 *
 * <p><b>Por que 30 dias y no 4 semanas.</b> Son cosas distintas y conviene no confundirlas: la
 * semana de programa la calcula {@code SemanaPrograma} de lunes a domingo (D-203; D-192 la habia hecho
 * bloques de siete dias del programa, sin llegar a produccion), y cuatro de esas dan 28 dias, no 30. Si
 * el mes se definiera como "cuatro semanas", el mes 3 terminaria el dia 84 y
 * quedarian seis dias del programa fuera de todo mes. Contando de a 30 dias los tres meses cubren
 * los 90 exactos y ninguno queda huerfano. La consecuencia es que el corte mensual no cae siempre
 * en domingo, y eso esta bien: el mes es un hito del plan, no una semana calendario.
 *
 * <p>Todo aca es aritmetica sobre el dia de programa, sin fechas ni zonas horarias, que es lo que
 * evita la clase de error registrada como E-91 en la bitacora.
 *
 * <p><b>Agregado 2026-10-31 (spec 026).</b> Lo de arriba es el programa de 90 dias. Con la duracion de cada persona (90 o 365, spec 022) los meses son
 * {@code duracion / 30} (3 o 12) y el ultimo llega hasta el ultimo dia: las versiones sin duracion siguen siendo las de 90.
 */
public final class MesPrograma {

    /** Tres meses: 30, 60 y 90 dias. Son los de un programa de 90 dias; los de cada persona salen de {@link #mesesDe(int)}. */
    public static final int MESES = 3;

    /** Dias que dura cada mes del programa. */
    public static final int DIAS_POR_MES = 30;

    /** Ultimo dia de un programa de 90 dias. Coincide con el cierre del mes 3. */
    public static final int ULTIMO_DIA = MESES * DIAS_POR_MES;

    /** Los meses del programa mas largo (365 dias): 12. Lo que admite la base. */
    public static final int MAXIMO_DE_MESES = DuracionDelPrograma.MAXIMA / DIAS_POR_MES;

    private MesPrograma() {
    }

    /**
     * Cuantos meses tiene un programa de {@code duracionDias} (spec 022): bloques de 30 dias y el ultimo absorbe el resto. 90 dias son 3 meses; 365,
     * 12 (once de 30 y uno de 35). Un programa de menos de 30 dias tiene un solo mes.
     */
    public static int mesesDe(int duracionDias) {
        return Math.max(DuracionDelPrograma.validar(duracionDias) / DIAS_POR_MES, 1);
    }

    /** Mes al que pertenece un dia, en un programa de {@code duracionDias}. El ultimo mes llega hasta el ultimo dia. */
    public static int deDia(int diaPrograma, int duracionDias) {
        int dia = Math.max(diaPrograma, 1);
        return Math.min((dia - 1) / DIAS_POR_MES + 1, mesesDe(duracionDias));
    }

    /** Ultimo dia de ese mes en un programa de {@code duracionDias}: el del ultimo mes es el ultimo dia del programa (90, o 365). */
    public static int ultimoDiaDe(int numeroMes, int duracionDias) {
        int meses = mesesDe(duracionDias);
        int mes = Math.min(Math.max(numeroMes, 1), meses);
        return mes == meses ? duracionDias : mes * DIAS_POR_MES;
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
     * calendario de lunes a domingo (D-203; con D-192, que no llego a produccion, fueron bloques de siete
     * dias del programa), y cuatro de esas dan 28 dias, no 30 — mezclarlas dejaria dias
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

    /** Igual, en un programa de {@code duracionDias}: el ultimo mes puede ser mas largo (35 dias en el de 365) y tener mas bloques. */
    public static int semanasQueQuedanDelMes(int diaPrograma, int duracionDias) {
        int dia = Math.min(Math.max(diaPrograma, 1), duracionDias);
        int diasQueQuedan = ultimoDiaDe(deDia(dia, duracionDias), duracionDias) - dia + 1;
        return Math.max((diasQueQuedan + DIAS_POR_SEMANA - 1) / DIAS_POR_SEMANA, 1);
    }

    /** {@code true} si el numero cae dentro de los meses que admite el programa mas largo (1 a {@link #MAXIMO_DE_MESES}); el de cada persona lo acota el servicio. */
    public static boolean esValido(int numeroMes) {
        return numeroMes >= 1 && numeroMes <= MAXIMO_DE_MESES;
    }

    /** {@code true} si el mes existe en un programa de {@code duracionDias}. */
    public static boolean esValido(int numeroMes, int duracionDias) {
        return numeroMes >= 1 && numeroMes <= mesesDe(duracionDias);
    }

    private static int acotar(int numeroMes) {
        return Math.min(Math.max(numeroMes, 1), MESES);
    }
}
