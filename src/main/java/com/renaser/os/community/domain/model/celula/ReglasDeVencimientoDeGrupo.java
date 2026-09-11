package com.renaser.os.community.domain.model.celula;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuándo avisarle al administrador que a un grupo se le acaba el periodo.
 *
 * <p>Pura: recibe el periodo ya leído y el día local ya resuelto. No consulta nada.
 *
 * <p><b>Para qué existe.</b> Un grupo programado se cierra el día que dice su periodo, y a partir
 * de ahí sus alumnos dejan de verlo. Sin aviso, eso ocurre en silencio y la gente se queda sin
 * grupo hasta que alguien se acuerda de programar el siguiente. El aviso es lo que convierte un
 * cierre automático en una decisión del administrador.
 */
public final class ReglasDeVencimientoDeGrupo {

    /**
     * Con cuánta antelación se avisa.
     *
     * <p>Una semana, y no dos días: lo que el administrador tiene que hacer con el aviso no es un
     * clic, es armar el grupo del mes siguiente —elegir mentor, repartir alumnos— y eso no se
     * despacha la víspera. Tampoco un mes: un aviso que llega demasiado pronto se archiva y para
     * cuando importa ya nadie lo recuerda.
     */
    public static final int DIAS_DE_ANTELACION = 7;

    private ReglasDeVencimientoDeGrupo() {
    }

    /**
     * Si hoy toca avisar de este grupo.
     *
     * <p>Devuelve true durante TODA la ventana —desde siete días antes hasta el último día—, no
     * solo el día exacto del umbral. Es deliberado: si el barrido no corre un día (despliegue,
     * caída, la instancia apagada), un aviso atado a un día exacto se pierde para siempre y nadie
     * se entera de que se perdió. Lo que evita el spam diario no es esta regla sino la clave de
     * deduplicación de abajo, que es la herramienta correcta para eso.
     *
     * <p>Un grupo ya vencido NO se avisa: llega tarde y el administrador ya no puede prevenir
     * nada. El aviso de "tienes alumnos sin grupo" sería otro aviso distinto, con otro texto y
     * otra urgencia; mezclarlos aquí haría que el mismo mensaje significara dos cosas.
     */
    public static boolean tocaAvisar(PeriodoGrupo periodo, LocalDate hoyLocal) {
        if (periodo == null || periodo.futuroEn(hoyLocal) || periodo.vencidoEn(hoyLocal)) {
            return false;
        }
        return periodo.diasRestantesEn(hoyLocal) <= DIAS_DE_ANTELACION;
    }

    /**
     * La clave que impide repetir el aviso.
     *
     * <p>Se ancla al GRUPO y a su fecha de cierre, no al día en que se detecta. Así el barrido
     * puede correr todos los días de la ventana —y debe, ver arriba— y el administrador recibe
     * UN aviso por grupo y por periodo. Es la misma decisión que {@code ReglasDeAviso} tomó para
     * los avisos de acompañamiento: anclar al episodio y no a la detección.
     *
     * <p>Si el administrador cambia la fecha de cierre, la clave cambia y vuelve a avisarse. Eso
     * es correcto y no un efecto colateral: mover el cierre es un episodio nuevo, y el aviso
     * anterior hablaba de una fecha que ya no existe.
     */
    public static UUID claveDeDeduplicacion(UUID celulaId, LocalDate finDelPeriodo) {
        String semilla = "grupo-por-vencer|" + celulaId + "|" + finDelPeriodo;
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }

    /** El texto que lee el administrador. Dice cuántos días quedan, que es lo accionable. */
    public static String cuerpoDelAviso(String nombreDelGrupo, long diasRestantes) {
        if (diasRestantes == 1) {
            return "El grupo " + nombreDelGrupo + " termina hoy. Programa el siguiente o mueve a sus alumnos.";
        }
        return "Al grupo " + nombreDelGrupo + " le quedan " + diasRestantes
                + " dias. Programa el siguiente o mueve a sus alumnos.";
    }
}
