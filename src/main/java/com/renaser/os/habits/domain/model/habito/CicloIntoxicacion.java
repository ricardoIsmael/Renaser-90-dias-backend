package com.renaser.os.habits.domain.model.habito;

/**
 * Los Ciclos de Intoxicacion Consciente (D-169), literal de la especificacion del cliente
 * ({@code docs/spec/Especificacion_Requisitos_Renaser_OS.docx}, regla de negocio de la Fase II):
 * <i>"Ocurren en ventanas fijas del programa: Dias 8-10 (VER), 17-19 (CORTAR) y 26-28 (RENASER).
 * En estos dias, todos los habitos se vuelven opcionales con excepcion de la publicacion diaria en
 * la comunidad."</i> Confirmado por el dueño el 2026-09-25.
 *
 * <p>Es la UNICA definicion del backend de "que dias son de intoxicacion". Quien la aplica es
 * {@link Habito#esOpcionalEnDia(int)}.
 *
 * <p><b>Recibe el dia de programa, no fechas, a proposito.</b> Ese dia ya llega DERIVADO de las
 * fechas en la zona del participante, con {@code dias_ajuste_programa} descontado
 * ({@code ParticipacionPrograma.diaProgramaDerivado} en {@code users}, regla 02 §2). Volver a
 * derivarlo aca pondria la cuenta del reloj en dos lugares, que es exactamente como se
 * desincronizo la columna generada que borro V22.
 *
 * <p>No es un {@link TipoDia}: el dia sigue siendo DISCIPLINA o DOMINGO para decidir que horario
 * rige, y la intoxicacion solo cambia si el habito es exigible (ver D-169).
 */
public enum CicloIntoxicacion {

    VER(8, 10),
    CORTAR(17, 19),
    RENASER(26, 28);

    /** Copia unica de {@code values()}: evita armar un arreglo nuevo por cada habito generado. */
    private static final CicloIntoxicacion[] CICLOS = values();

    private final int primerDia;
    private final int ultimoDia;

    CicloIntoxicacion(int primerDia, int ultimoDia) {
        this.primerDia = primerDia;
        this.ultimoDia = ultimoDia;
    }

    /**
     * Si ese dia de programa cae dentro de alguna de las tres ventanas. El dia 0 (programa sin
     * arrancar) y cualquier valor fuera de 1..90 nunca lo son: las ventanas son fijas.
     */
    public static boolean esDiaDeIntoxicacion(int diaPrograma) {
        for (CicloIntoxicacion ciclo : CICLOS) {
            if (ciclo.contiene(diaPrograma)) {
                return true;
            }
        }
        return false;
    }

    private boolean contiene(int diaPrograma) {
        return diaPrograma >= primerDia && diaPrograma <= ultimoDia;
    }
}
