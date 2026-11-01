package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/** Compuertas de rocas fijas para las pruebas de las herramientas que proponen (D-247). */
final class CompuertasDePrueba {

    /** Con las tres Rocas Maestras y objetivo semanal en todos los ejes: la propuesta sale como siempre. */
    static final CompuertaParaProponer LIBRE = compuerta(true, List.of());

    private CompuertasDePrueba() {
    }

    /** Sin Rocas Maestras: todavia no escribio sus tres objetivos de 90 dias. */
    static CompuertaParaProponer sinRocasMaestras() {
        return compuerta(false, List.of("CUERPO", "TRABAJO", "RELACIONES"));
    }

    static CompuertaParaProponer sinObjetivoSemanal(List<String> ejes) {
        return compuerta(true, ejes);
    }

    /** Sin Rocas Maestras, de una persona cuyo programa y formación se dicen con otro plazo (spec 022). */
    static CompuertaParaProponer sinRocasMaestrasDe(com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort situacion) {
        return new CompuertaParaProponer(new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return false;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return List.of("CUERPO", "TRABAJO", "RELACIONES");
            }
        }, situacion);
    }

    private static CompuertaParaProponer compuerta(boolean maestras, List<String> sinSemanal) {
        ConsultarCompuertaDeRocasPort port = new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return maestras;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return sinSemanal;
            }
        };
        return new CompuertaParaProponer(port, participanteId -> java.util.Optional.empty());
    }
}
