package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ObjetivoDelMes;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDelDia;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;

import java.math.BigDecimal;
import java.util.List;

/**
 * El texto que devuelve {@link ConsultarRocasHerramienta}: una linea por roca, escrita para que el
 * modelo la parafrasee. Mismo criterio que las demas herramientas: las marcas raras (bloqueada,
 * revisada) se nombran solo cuando SON ciertas.
 */
final class TextoDeRocas {

    private TextoDeRocas() {
    }

    /** @param conEstadoDelDia {@code true} para hoy: se dice si esta completada, pendiente o bloqueada */
    static String delDia(String nombreDelDia, RocasDelDia dia, boolean conEstadoDelDia) {
        StringBuilder texto = new StringBuilder("Rocas de ").append(nombreDelDia).append(" (")
                .append(dia.fecha()).append("):\n");
        if (dia.rocas().isEmpty()) {
            texto.append("No tiene rocas planificadas para ").append(nombreDelDia).append(".\n");
        }
        dia.rocas().forEach(roca -> texto.append(lineaDe(roca, conEstadoDelDia)).append('\n'));
        return texto.append(lineaDe(dia.planDeManana())).toString();
    }

    static String deLaSemana(RocasDeLaSemana semana) {
        StringBuilder texto = new StringBuilder("Objetivos de la semana ").append(semana.numeroSemana())
                .append(" del programa (").append(semana.inicio()).append(" al ").append(semana.fin()).append("):\n");
        if (semana.rocas().isEmpty()) {
            return texto.append("Todavia no armo los objetivos de esta semana.").toString();
        }
        semana.rocas().forEach(roca -> texto.append(lineaDe(roca)).append('\n'));
        return texto.toString().trim();
    }

    static String delMes(List<ObjetivoDelMes> objetivos) {
        if (objetivos.isEmpty()) {
            return "No tiene objetivo del mes: todavia no definio sus Rocas Maestras o el programa no esta en curso.";
        }
        StringBuilder texto = new StringBuilder("Objetivo del mes en curso, por eje (cifra = donde tiene que "
                + "estar al cierre del mes; en una meta que se acumula, lo que tiene que sumar ese mes):\n");
        objetivos.forEach(objetivo -> texto.append(lineaDe(objetivo)).append('\n'));
        return texto.toString().trim();
    }

    private static String lineaDe(RocaDelDia roca, boolean conEstadoDelDia) {
        StringBuilder linea = new StringBuilder("- ").append(roca.eje()).append(" #").append(roca.posicion())
                .append(' ').append(roca.color()).append(" | ").append(roca.titulo())
                .append(" | ").append(franjaDe(roca));
        if (!conEstadoDelDia) {
            return linea.append(" | estado=planificada").toString();
        }
        linea.append(" | estado=").append(roca.completada() ? "completada" : "pendiente")
                .append(" | evidencia=").append(roca.completada() ? "entregada" : "pendiente");
        if (roca.bloqueadaPorPareto()) {
            linea.append(" | bloqueada=si (primero tiene que completar la roca VERDE de su eje)");
        }
        return linea.toString();
    }

    private static String franjaDe(RocaDelDia roca) {
        if (roca.horaInicio() == null && roca.horaFin() == null) {
            return "sin hora fija";
        }
        return "inicio=" + (roca.horaInicio() == null ? "-" : roca.horaInicio())
                + " fin=" + (roca.horaFin() == null ? "-" : roca.horaFin());
    }

    private static String lineaDe(PlanDeManana plan) {
        String creado = plan.creado() ? "ya esta creado (" + plan.rocasPlanificadas() + " roca(s))"
                : "todavia no esta creado";
        return "Plan de manana: " + creado + ". La ventana de planificacion abre a las " + plan.ventanaAbreA()
                + " (hora local) y ahora esta " + (plan.ventanaAbierta() ? "abierta" : "cerrada")
                + ". La app " + (plan.puedeCrearlo() ? "si" : "no") + " le deja crear el plan de manana ahora.";
    }

    private static String lineaDe(RocaDeLaSemana roca) {
        StringBuilder linea = new StringBuilder("- ").append(roca.eje()).append(" | ").append(roca.titulo());
        if (roca.obstaculo() != null) {
            linea.append(" | obstaculo=").append(roca.obstaculo());
        }
        if (roca.contingencia() != null) {
            linea.append(" | contingencia=").append(roca.contingencia());
        }
        linea.append(" | editable=").append(roca.editable() ? "si" : "no");
        if (roca.revisada()) {
            linea.append(" | revision_de_cierre=hecha");
        }
        return linea.toString();
    }

    private static String lineaDe(ObjetivoDelMes objetivo) {
        StringBuilder linea = new StringBuilder("- ").append(objetivo.eje()).append(" | mes ")
                .append(objetivo.numeroMes()).append(" (cierra el dia ").append(objetivo.diaDeCierre())
                .append(" del programa)");
        if (objetivo.tituloPropio() != null) {
            linea.append(" | objetivo escrito por la persona: ").append(objetivo.tituloPropio());
        }
        if (objetivo.metaAlcanzada()) {
            return linea.append(" | ya alcanzo la meta de los 90 dias (").append(cifraDe(objetivo))
                    .append("): el mes es sostenerla").toString();
        }
        if (objetivo.cifra() != null) {
            linea.append(" | cifra=").append(cifraDe(objetivo));
        }
        if (objetivo.motivoSinCifra() != null) {
            linea.append(" | sin cifra, motivo=").append(objetivo.motivoSinCifra());
        }
        return linea.toString();
    }

    private static String cifraDe(ObjetivoDelMes objetivo) {
        BigDecimal cifra = objetivo.cifra();
        if (cifra == null) {
            return "-";
        }
        String numero = cifra.stripTrailingZeros().toPlainString();
        if (objetivo.unidad() == null || objetivo.unidad().isBlank()) {
            return numero;
        }
        return objetivo.unidadAdelante() ? objetivo.unidad() + " " + numero : numero + " " + objetivo.unidad();
    }
}
