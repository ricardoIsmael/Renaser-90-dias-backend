package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.ProximoHito;
import com.renaser.os.rag.domain.model.mapa.ResumenDelMapa;

import java.util.Locale;

/**
 * La linea del Mapa de Renacimiento dentro de "Donde esta la persona ahora mismo" (D-233): a lo sumo
 * UNA, con la prioridad y el proximo hito, porque va en cada turno. Lo demas del Mapa lo da la
 * herramienta {@code consultar_mi_mapa}.
 *
 * <p>El texto del hito lo escribio la persona y termina en el prompt de SISTEMA: se aplana a una linea y
 * se acota, mismo criterio que los titulos de {@link HabitosDeHoyEnElPrompt}.
 */
final class MapaEnElPrompt {

    private static final int LARGO_MAXIMO_DEL_HITO = 90;

    private MapaEnElPrompt() {
    }

    /**
     * Sin Rocas Maestras (D-247, E-496): planificar objetivos esta cerrado, y la linea lo dice para que el
     * modelo ni lo intente. Con el Mapa respondido y sin ellas, la activacion no las creo: tambien se dice.
     */
    static final String SIN_COMPLETAR = "Mapa de Renacimiento: sin completar — no se puede planificar NINGUN "
            + "objetivo (acciones del dia, plan de la semana): todo se rechaza. Si pide planificar su dia o sus "
            + "objetivos, antes de preguntarle nada dile eso e invitala a completarlo en Plan, \"Ir al Mapa de "
            + "Renacimiento\". Sus habitos si se ajustan.\n";
    static final String SIN_ROCAS_MAESTRAS = "Mapa de Renacimiento: respondido, pero sus objetivos de 90 dias "
            + "(Rocas Maestras) no quedaron creados — no se puede planificar NINGUN objetivo (acciones del dia, plan "
            + "de la semana): todo se rechaza. Si pide planificar su dia o sus objetivos, antes de preguntarle nada "
            + "dile eso y sugierele escribir a soporte. Sus habitos si se ajustan.\n";

    /** Vacio si no se sabe nada del Mapa ({@code null}): la situacion queda como estaba. */
    static String linea(ResumenDelMapa mapa, int diaPrograma) {
        if (mapa == null) {
            return "";
        }
        if (mapa.objetivosBloqueados()) {
            return mapa.tieneMapa() ? SIN_ROCAS_MAESTRAS : SIN_COMPLETAR;
        }
        if (!mapa.tieneMapa()) {
            return diaPrograma < 7 ? "Mapa de Renacimiento: todavia no (se arma el dia 7).\n"
                    : "Mapa de Renacimiento: no lo tiene guardado.\n";
        }
        String prioridad = mapa.prioridad() == null ? "sin prioridad elegida"
                : "prioridad " + MapaDeLaPersona.nombreDeArea(mapa.prioridad());
        return "Mapa de Renacimiento: " + prioridad + proximo(mapa)
                + ". Sus metas, su porque y su protocolo de retorno no estan aca: llama consultar_mi_mapa.\n";
    }

    private static String proximo(ResumenDelMapa mapa) {
        ProximoHito proximo = mapa.proximo();
        if (proximo == null) {
            return "";
        }
        String cuando = proximo.faltan() == 0 ? "es hoy"
                : "en " + proximo.faltan() + (proximo.faltan() == 1 ? " dia" : " dias");
        String texto = mapa.hito() == null ? ", sin texto escrito"
                : ", " + MapaDeLaPersona.nombreDeArea(mapa.hito().area()).toLowerCase(Locale.ROOT)
                        + ": \"" + acotado(mapa.hito().texto()) + "\"";
        return "; proximo hito dia " + proximo.dia() + " (" + cuando + ")" + texto;
    }

    private static String acotado(String texto) {
        String plano = texto.replaceAll("\\s+", " ").replace('"', '\'').trim();
        return plano.length() <= LARGO_MAXIMO_DEL_HITO ? plano : plano.substring(0, LARGO_MAXIMO_DEL_HITO) + "...";
    }
}
