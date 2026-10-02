package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/** Compuertas de rocas fijas para las pruebas de las herramientas que proponen (D-247). */
final class CompuertasDePrueba {

    /** Con las tres Rocas Maestras y objetivo semanal en todos los ejes: la propuesta sale como siempre. */
    static final CompuertaParaProponer LIBRE = compuerta(true, List.of(), false);

    private CompuertasDePrueba() {
    }

    /** Sin Rocas Maestras; {@code conMapa} = el Mapa esta respondido (la activacion no las creo). */
    static CompuertaParaProponer sinRocasMaestras(boolean conMapa) {
        return compuerta(false, List.of("CUERPO", "TRABAJO", "RELACIONES"), conMapa);
    }

    static CompuertaParaProponer sinObjetivoSemanal(List<String> ejes) {
        return compuerta(true, ejes, true);
    }

    private static CompuertaParaProponer compuerta(boolean maestras, List<String> sinSemanal, boolean conMapa) {
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
        return new CompuertaParaProponer(port, id -> conMapa
                ? new MapaDeLaPersona(true, true, "salud", List.of(), List.of(), null, List.of(), List.of())
                : MapaDeLaPersona.sinMapa());
    }
}
