package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.ProximoHito;
import com.renaser.os.rag.domain.model.mapa.ResumenDelDiagnostico;
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
     * Sin Rocas Maestras (D-247, E-496): planificar objetivos esta cerrado hasta que la persona tenga sus tres objetivos de 90 dias, y la linea lo dice para
     * que el modelo ni lo intente. Desde la spec 026 el camino es escribirlos en Plan (o confirmar los que nacieron de su diagnostico): el Mapa se retiro.
     */
    static final String SIN_OBJETIVOS = "Objetivos de 90 dias: todavia no los tiene escritos — no se puede planificar NINGUN objetivo "
            + "(acciones del dia, plan de la semana): todo se rechaza. Si pide planificar su dia o sus objetivos, antes de preguntarle nada dile eso "
            + "e invitala a escribirlos en la pestana Plan (o a confirmar los de su diagnostico). Sus habitos si se ajustan.\n";

    /** Vacio si no se sabe nada ({@code null}): la situacion queda como estaba. Lleva el diagnostico y, si hizo el Mapa antes de que se retirara, su prioridad y su proximo hito. */
    static String linea(ResumenDelMapa mapa, int diaPrograma) {
        if (mapa == null) {
            return "";
        }
        if (mapa.objetivosBloqueados()) {
            return SIN_OBJETIVOS + lineaDelDiagnostico(mapa);
        }
        return lineaDelDiagnostico(mapa) + lineaDelMapa(mapa);
    }

    private static String lineaDelDiagnostico(ResumenDelMapa mapa) {
        ResumenDelDiagnostico d = mapa.diagnostico();
        if (d == null) {
            return "";
        }
        if (!d.completado()) {
            return "Diagnostico inicial: lo tiene asignado y todavia no lo termina.\n";
        }
        String prioridad = d.prioridad() == null ? "" : "; prioridad principal: " + MapaDeLaPersona.nombreDeArea(areaDelEje(d.prioridad()));
        return "Diagnostico inicial: completado" + prioridad + ". Sus respuestas completas no estan aca: llama consultar_mi_diagnostico.\n";
    }

    private static String areaDelEje(String eje) {
        String area = MapaDeLaPersona.areaDelEje(eje);
        return area == null ? eje : area;
    }

    /** El Mapa solo se menciona a quien lo hizo antes de que se retirara (spec 026). */
    private static String lineaDelMapa(ResumenDelMapa mapa) {
        if (!mapa.tieneMapa()) {
            return "";
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
