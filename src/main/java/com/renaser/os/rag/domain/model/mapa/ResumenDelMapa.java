package com.renaser.os.rag.domain.model.mapa;

/**
 * Lo minimo del Mapa que va en CADA turno (D-233): la prioridad y el proximo hito del area que manda,
 * para que el acompanante no tenga que llamar {@code consultar_mi_mapa} solo para ubicarse. Todo lo
 * demas (metas, porque, retorno) queda en la herramienta: el prompt se paga en cada mensaje.
 *
 * @param tieneMapa   {@code false} = no lo recorrio (lo demas viene en {@code null})
 * @param prioridad   codigo de area ({@code salud}...), o {@code null} si no la eligio
 * @param proximo     el proximo hito segun el dia de hoy, o {@code null} si no hay (antes del dia 1,
 *                    despues del 90, o sin dia de programa)
 * @param hito        lo que escribio para ese hito en el area que manda (o en la primera que lo tenga);
 *                    {@code null} si no escribio nada para ese dia
 * @param rocasMaestrasCompletas si {@code rocks} tiene sus tres objetivos de 90 dias, la llave para planificar
 *                    (D-247): {@code false} = toda propuesta de objetivos fallaria; {@code null} = no se sabe.
 *                    Va aparte de {@code tieneMapa}: puede haber Mapa respondido y la activacion sin crearlas
 */
public record ResumenDelMapa(boolean tieneMapa, String prioridad, ProximoHito proximo, MapaDeLaPersona.Hito hito,
                             Boolean rocasMaestrasCompletas) {

    public static ResumenDelMapa de(MapaDeLaPersona mapa, int diaDeHoy) {
        if (!mapa.tieneMapa()) {
            return new ResumenDelMapa(false, null, null, null, null);
        }
        ProximoHito proximo = ProximoHito.para(diaDeHoy).orElse(null);
        MapaDeLaPersona.Hito hito = proximo == null ? null : mapa.hitoPrincipalDelDia(proximo.dia()).orElse(null);
        return new ResumenDelMapa(true, mapa.prioridad(), proximo, hito, null);
    }

    /** El mismo resumen sabiendo si tiene sus Rocas Maestras ({@code null} si no se pudo saber). */
    public ResumenDelMapa conRocasMaestras(Boolean completas) {
        return new ResumenDelMapa(tieneMapa, prioridad, proximo, hito, completas);
    }

    /** Sin las Rocas Maestras, sabido: no se puede planificar ningun objetivo. */
    public boolean objetivosBloqueados() {
        return Boolean.FALSE.equals(rocasMaestrasCompletas);
    }
}
