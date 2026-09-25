package com.renaser.os.notifications.domain.model.semaforo;

/**
 * Los textos de los avisos del sábado del semáforo, según el caso (D-168; decisión del dueño del
 * 2026-09-25: «automáticos viendo todos los posibles casos y resilientes»).
 *
 * <p>Reglas duras, porque el push se ve en la pantalla bloqueada y lleva el mismo texto que la
 * bandeja: <b>sin cifras, sin colores ni sus palabras, sin nombres de personas</b>. El nombre del
 * grupo sí va. El resultado de cada uno se ve adentro de la app.
 *
 * <p><b>Nunca falla:</b> un dato que falta o no cierra da el texto neutro de siempre, y un grupo sin
 * nombre se dice «tu grupo». No hay texto vacío ni con huecos.
 */
public final class RedaccionDelSemaforo {

    private static final String TU_GRUPO = "tu grupo";
    /** Un nombre largo se corta: el cuerpo del push se trunca en la pantalla del teléfono. */
    private static final int LARGO_MAXIMO_DEL_GRUPO = 60;

    private RedaccionDelSemaforo() {
    }

    /** A la persona: si en la semana tuvo algo programado o no. */
    public static AvisoRedactado paraLaPersona(boolean tuvoAlgoProgramado) {
        if (!tuvoAlgoProgramado) {
            return new AvisoRedactado(CasoDelAviso.SIN_REGISTROS, "Tu semana ya cerró",
                    "Esta semana no tuviste hábitos ni objetivos programados. Planifica los de esta semana "
                            + "para que cuenten en tu semáforo.");
        }
        return new AvisoRedactado(CasoDelAviso.CON_REGISTROS, "Tu semana ya cerró",
                "Mira cómo te fue en tu semáforo de la semana.");
    }

    /** Al mentor, por su grupo. */
    public static AvisoRedactado paraElMentor(String grupoNombre, ConteoDeLaSemana conteo) {
        String grupo = nombreDelGrupo(grupoNombre);
        CasoDelAviso caso = casoDe(conteo);
        String cuerpo = switch (caso) {
            case NECESITAN_APOYO -> "En " + grupo + " hay aprendices que necesitan tu apoyo. Mira el semáforo del grupo.";
            case SIN_REGISTROS -> "En " + grupo + ", nadie tuvo registros en el semáforo esta semana.";
            case SIN_ALERTAS -> "En " + grupo + ", nadie necesita apoyo extra esta semana. ¡Buen acompañamiento!";
            default -> "El semáforo de " + grupo + " ya está listo.";
        };
        return new AvisoRedactado(caso, "Tu grupo cerró la semana", cuerpo);
    }

    /** A líder, administración y alquimia: todos los grupos juntos. */
    public static AvisoRedactado paraLaConduccion(ConteoDeLaSemana conteo) {
        CasoDelAviso caso = casoDe(conteo);
        String cuerpo = switch (caso) {
            case NECESITAN_APOYO -> "Hay grupos con aprendices que necesitan apoyo. Mira el semáforo por grupos.";
            case SIN_REGISTROS -> "Ningún grupo tuvo registros en el semáforo esta semana.";
            case SIN_ALERTAS -> "Ningún grupo tiene aprendices que necesiten apoyo extra esta semana.";
            default -> "El semáforo de los grupos ya está listo.";
        };
        return new AvisoRedactado(caso, "Semana cerrada", cuerpo);
    }

    private static CasoDelAviso casoDe(ConteoDeLaSemana conteo) {
        return conteo == null ? CasoDelAviso.NEUTRO : conteo.caso();
    }

    private static String nombreDelGrupo(String grupoNombre) {
        String nombre = grupoNombre == null ? "" : grupoNombre.strip();
        if (nombre.isEmpty()) {
            return TU_GRUPO;
        }
        return nombre.length() <= LARGO_MAXIMO_DEL_GRUPO
                ? nombre
                : nombre.substring(0, LARGO_MAXIMO_DEL_GRUPO).strip() + "…";
    }
}
