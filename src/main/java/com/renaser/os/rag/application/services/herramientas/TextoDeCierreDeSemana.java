package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.Motivo;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.RevisionDelEje;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Los textos de {@code proponer_cerrar_semana} (2026-09-23): el resumen que ve la persona junto a los
 * botones, los motivos para no proponer, y lo que vuelve al confirmar.
 *
 * <p>El resumen dice EXACTAMENTE que se va a guardar por eje: la autoevaluacion, el bloqueo principal
 * y la correccion, que son los tres datos de {@code CerrarSemanaUseCase}. Nada mas se guarda.
 */
final class TextoDeCierreDeSemana {

    /** Verificado el 2026-09-23: {@code CerrarSemanaUseCase} no publica eventos y {@code points} no lee la revision. */
    static final String SIN_PUNTOS = " Cerrar la semana no suma puntos.";

    private TextoDeCierreDeSemana() {
    }

    /** "Cerrar la semana 3 (2026-09-21 al 2026-09-27). Trabajo (Cerrar 2 ventas): autoevaluacion 7/10; ..." */
    static String resumen(RocasDeLaSemana semana, List<RevisionDelEje> revisiones, ReglasDelCierre reglas) {
        String ejes = revisiones.stream().map(r -> lineaDe(semana, r, reglas)).collect(Collectors.joining(". "));
        return "Cerrar la semana " + semana.numeroSemana() + " (" + semana.inicio() + " al " + semana.fin() + "). "
                + ejes + ". Una vez cerrada, desde el chat no se reescribe.";
    }

    /** Vacio si se puede proponer; si no, el motivo para el modelo. */
    static Optional<String> motivoParaNoProponer(RocasDeLaSemana semana, List<RevisionDelEje> revisiones) {
        if (semana.rocas().isEmpty()) {
            return Optional.of("La semana " + semana.numeroSemana() + " no tiene objetivos semanales: no hay nada "
                    + "que cerrar.");
        }
        for (RevisionDelEje revision : revisiones) {
            Optional<RocaDeLaSemana> roca = rocaDelEje(semana, revision.eje());
            if (roca.isEmpty()) {
                return Optional.of("La semana " + semana.numeroSemana() + " no tiene objetivo de "
                        + nombreDelEje(revision.eje()) + ": ese eje no se puede cerrar. Cierra solo los ejes que "
                        + "tienen objetivo (consultar_rocas con alcance semana).");
            }
            if (roca.get().revisada()) {
                return Optional.of(nombreDelEje(revision.eje()) + " ya tiene su cierre de la semana "
                        + semana.numeroSemana() + ". Desde el chat no se reescribe: si quiere corregirlo, lo hace en "
                        + "la app. Propone solo los ejes que faltan.");
            }
        }
        return Optional.empty();
    }

    static String cerrada(int numeroSemana, List<String> ejes) {
        return "Semana " + numeroSemana + " cerrada: " + ejes.stream().map(TextoDeCierreDeSemana::nombreDelEje)
                .collect(Collectors.joining(", ")) + ".";
    }

    /** Lo que se le dice cuando {@code rocks} rechaza el cierre al confirmar. */
    static String rechazo(Motivo motivo) {
        return switch (motivo) {
            case SIN_PROGRAMA -> "No encontre un programa activo para esta cuenta.";
            case SIN_ACCESO -> "No se pudo cerrar la semana: la cuenta esta suspendida o todavia no tiene el "
                    + "programa de rocas activo.";
            case SIN_OBJETIVO_SEMANAL -> "No se cerro nada: algun eje ya no tiene objetivo esa semana.";
            case YA_REVISADA -> "No se cerro nada: algun eje ya tenia su cierre (quizas lo hizo en la app) y desde "
                    + "el chat no se reescribe. Si quiere corregirlo, lo hace en la app.";
            case DATOS_INVALIDOS -> "No se pudo cerrar la semana: revise que haya una sola revision por eje, con la "
                    + "autoevaluacion, el bloqueo principal y la correccion.";
        };
    }

    private static String lineaDe(RocasDeLaSemana semana, RevisionDelEje revision, ReglasDelCierre reglas) {
        String objetivo = rocaDelEje(semana, revision.eje()).map(r -> " (" + r.titulo() + ")").orElse("");
        return nombreDelEje(revision.eje()) + objetivo + ": autoevaluacion " + revision.autoevaluacion() + "/"
                + reglas.autoevaluacionMaxima() + "; bloqueo principal: " + revision.bloqueoPrincipal()
                + "; correccion: " + revision.correccion();
    }

    private static Optional<RocaDeLaSemana> rocaDelEje(RocasDeLaSemana semana, String eje) {
        return semana.rocas().stream().filter(r -> r.eje().equals(eje)).findFirst();
    }

    private static String nombreDelEje(String eje) {
        return TextoDePlanDeRocas.nombreDelEje(eje);
    }
}
