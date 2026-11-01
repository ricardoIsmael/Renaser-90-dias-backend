package com.renaser.os.leadership.domain.model.habitos;

/**
 * Cómo le fue a UNA persona con UN hábito en UN día (spec 029). Son los mismos estados de la ficha de la persona:
 * <ul>
 *   <li>{@link #CUMPLIDO}: lo cumplió.</li>
 *   <li>{@link #PENDIENTE}: no lo cumplió y su día todavía no cerró (hoy): todavía puede.</li>
 *   <li>{@link #SIN_CUMPLIR}: su día cerró sin cumplirlo.</li>
 *   <li>{@link #SIN_DATOS}: no hay nada que medir —no le tocaba, estaba en pausa, todavía no empezaba o era opcional y no lo hizo—.
 *       <b>Nunca es un incumplimiento</b> ni baja un porcentaje.</li>
 * </ul>
 * Lo no hecho de hoy se llama «pendiente» y no «sin cumplir» porque el día no cerró; quien lo decide es el propio registro (el barrido de
 * expiración lo cierra en el día local de cada persona), no el reloj del servidor.
 */
public enum EstadoEnElDia {

    CUMPLIDO(2),
    PENDIENTE(1),
    SIN_CUMPLIR(0),
    SIN_DATOS(3);

    private final int ordenEnLaLista;

    EstadoEnElDia(int ordenEnLaLista) {
        this.ordenEnLaLista = ordenEnLaLista;
    }

    /**
     * @param cumplido        el registro está completado
     * @param vencidoSinCumplir el día cerró sin cumplirse (fallido o expirado)
     * @param opcional        ese día no era exigible
     */
    public static EstadoEnElDia de(boolean cumplido, boolean vencidoSinCumplir, boolean opcional) {
        if (cumplido) {
            return CUMPLIDO;
        }
        if (opcional) {
            return SIN_DATOS;
        }
        return vencidoSinCumplir ? SIN_CUMPLIR : PENDIENTE;
    }

    /** Lo esperado de un hábito: a quienes ese día les tocaba hacerlo. */
    public boolean cuentaComoEsperado() {
        return this != SIN_DATOS;
    }

    /** En la lista de personas, primero a quien hay que escribirle (sin cumplir, pendiente), después los que cumplieron y al final los sin datos. */
    public int ordenEnLaLista() {
        return ordenEnLaLista;
    }
}
