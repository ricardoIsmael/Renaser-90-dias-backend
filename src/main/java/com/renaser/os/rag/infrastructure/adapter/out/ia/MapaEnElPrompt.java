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
    static final String SIN_OBJETIVOS = sinObjetivos("90 días");

    /**
     * Spec 038: con el plazo de ESA persona («90 dias», «3 meses», «1 año»): la Escuela, el Diplomado y la Certificacion no tienen el mismo. Tu no los
     * escribes ni los confirmas: los escribe o los confirma ella en Plan.
     */
    static String sinObjetivos(String plazoDelObjetivo) {
        return sinObjetivos(plazoDelObjetivo, false);
    }

    /**
     * Issue #63: antes de que empiece su programa (dia 0, la situacion de arriba dice «empieza manana»), la linea de los objetivos mandaba a Plan «antes de
     * preguntarle nada» y era lo unico que el modelo repetia: en la Escuela contestaba «tus objetivos de 90 dias estan pendientes» a «Armemos mi plan» sin decir
     * que su programa empieza manana. Con {@code programaPorEmpezar} le manda decir PRIMERO cuando empieza, y despues lo de los objetivos. No depende de la
     * formacion: el plazo es lo unico que cambia. (Solo el Diplomado lleva ademas la linea del nombre de su objetivo grande; por eso el Diplomado lo decia y la Escuela no.)
     * Sin diagnostico: solo «escribelos en Plan» (issue #65).
     */
    static String sinObjetivos(String plazoDelObjetivo, boolean programaPorEmpezar) {
        return sinObjetivos(plazoDelObjetivo, programaPorEmpezar, false);
    }

    /**
     * Issue #65: con un diagnostico terminado los objetivos nacieron de el y los confirma en Plan; sin diagnostico (la ficha estandar de la Escuela, por
     * ejemplo) solo los escribe ella en Plan y la linea NO nombra ningun diagnostico: SER le decia «confirma los de tu diagnostico» a quien nunca lo tuvo.
     */
    static String sinObjetivos(String plazoDelObjetivo, boolean programaPorEmpezar, boolean nacenDeSuDiagnostico) {
        String queDecirPrimero = programaPorEmpezar
                ? "dile PRIMERO cuando empieza su programa (la fecha de arriba; nunca contestes solo que sus objetivos estan pendientes) y despues eso, "
                : "dile eso ";
        String ahi = nacenDeSuDiagnostico
                ? "ahi escribe sus tres objetivos (o confirma los que nacieron de su diagnostico). "
                : "ahi escribe sus tres objetivos (\"Escribelos en Plan\"). ";
        return "Objetivos de " + plazoDelObjetivo + ": todavia no los tiene escritos ni confirmados — no se puede planificar NINGUN objetivo "
                + "(acciones del dia, plan de la semana): todo se rechaza. Si pide planificar su dia o sus objetivos, antes de preguntarle nada "
                + queDecirPrimero + "e invitala a abrir la pestana Plan: " + ahi
                + "Tu no puedes escribirlos ni confirmarlos. Sus habitos si se ajustan.\n";
    }

    /** Vacio si no se sabe nada ({@code null}): la situacion queda como estaba. Lleva el diagnostico y, si hizo el Mapa antes de que se retirara, su prioridad y su proximo hito. */
    static String linea(ResumenDelMapa mapa, int diaPrograma) {
        return linea(mapa, diaPrograma, "90 días");
    }

    /** Igual, diciendo los objetivos con el plazo de la persona (spec 038). */
    static String linea(ResumenDelMapa mapa, int diaPrograma, String plazoDelObjetivo) {
        if (mapa == null) {
            return "";
        }
        if (mapa.objetivosBloqueados()) {
            ResumenDelDiagnostico d = mapa.diagnostico();
            return sinObjetivos(plazoDelObjetivo, diaPrograma < 1, d != null && d.completado()) + lineaDelDiagnostico(mapa);
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
