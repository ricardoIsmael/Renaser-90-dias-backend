package com.renaser.os.community.domain.model.celula;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Los dos avisos al staff de que falta armar algo (D-240): no hay ningún grupo en curso al que
 * trasladar a quien terminó la bienvenida, o hay un grupo en curso sin mentor.
 *
 * <p>Pura: recibe ids y el día local ya resuelto. Lo que evita el spam es la clave: el barrido y el
 * traslado corren cada hora y la bandeja tiene un índice único por (usuario, tipo, clave), así que
 * cada persona recibe UN aviso por cosa y por día local, por más veces que se detecte. Un día después,
 * si sigue igual, vuelve a avisarse: mismo criterio que {@link ReglasDeVencimientoDeGrupo}, anclar la
 * clave al episodio y no a la detección.
 */
public final class AvisoDeArmadoDeGrupos {

    /**
     * Desde qué hora local se avisa de un grupo sin mentor. El barrido corre cada hora y el día del
     * grupo empieza a medianoche: sin este umbral, el aviso (que también sale como push) le llegaría
     * al líder a las 00:15. Las 07:00 son una elección operativa, a confirmar por el dueño.
     */
    public static final LocalTime DESDE_LAS = LocalTime.of(7, 0);

    private AvisoDeArmadoDeGrupos() {
    }

    public static boolean esHoraDeAvisar(LocalTime horaLocal) {
        return !horaLocal.isBefore(DESDE_LAS);
    }

    /** Una por cohorte y día: diez aprendices esperando el mismo día son UN aviso, no diez. */
    public static UUID claveSinGrupoEnCurso(UUID cohorteId, LocalDate diaLocal) {
        return clave("sin-grupo-en-curso|" + cohorteId + "|" + diaLocal);
    }

    /** Una por grupo y día. */
    public static UUID claveGrupoSinMentor(UUID celulaId, LocalDate diaLocal) {
        return clave("grupo-sin-mentor|" + celulaId + "|" + diaLocal);
    }

    public static String cuerpoSinGrupoEnCurso() {
        return "Hay aprendices que terminaron la semana de bienvenida y no hay ningun grupo en curso para "
                + "recibirlos. Siguen en la bienvenida hasta que crees un grupo.";
    }

    public static String cuerpoGrupoSinMentor(String nombreDelGrupo) {
        return "El grupo " + nombreDelGrupo + " esta en curso y no tiene mentor. Asignale uno.";
    }

    private static UUID clave(String semilla) {
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }
}
