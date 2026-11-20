package com.renaser.os.rocks.api;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDeNoventaDias;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Los objetivos de un aprendiz juntos y solo para leer: los de los 90 días (o los de su formación), el del mes en curso y los de la
 * semana (spec 056 FR-027 para el líder; spec 057 FR-001 para el mentor). Quien lo pide autoriza antes; aquí no hay ninguna regla de
 * acceso, solo se compone lo que ya da {@link RocasDelAprendizFinder} y se suma el plazo y la fase de la persona.
 *
 * <p>Propaga lo mismo que {@link RocasDelAprendizFinder}: {@code NoSuchElementException} si la persona no existe y
 * {@code NotAuthorizedException} si está suspendida o no tiene el programa andando.
 */
public interface ObjetivosDelAprendizFinder {

    ObjetivosDelAprendiz objetivosDe(UserId aprendizId);

    /**
     * @param noventaDias  uno por eje con Roca Maestra definida; vacío si todavía no los definió
     * @param mes          el mes en curso; null si no hay objetivos del mes (programa sin arrancar o sin objetivos)
     * @param semana       la semana de programa en curso; null si no tiene objetivos semanales
     * @param duracionDias el plazo de la formación de ESTA persona (90, 365…): nunca se escribe a mano en la pantalla
     * @param fase         la fase del programa en la que va hoy; con el día 0 es la primera
     */
    record ObjetivosDelAprendiz(List<ObjetivoDeNoventaDias> noventaDias, ObjetivosDelMes mes, ObjetivosDeLaSemana semana,
                                int duracionDias, FaseDelPrograma fase) {

        public ObjetivosDelAprendiz {
            noventaDias = noventaDias == null ? List.of() : List.copyOf(noventaDias);
        }
    }

    /** @param objetivos uno por eje con objetivo del mes en curso; {@code tituloPropio} es lo que la persona escribió, si lo hizo */
    record ObjetivosDelMes(int numeroMes, int diaDeCierre, List<ObjetivoDelMesDelEje> objetivos) {

        public ObjetivosDelMes {
            objetivos = objetivos == null ? List.of() : List.copyOf(objetivos);
        }
    }

    /**
     * @param inicio       lunes (o el inicio del programa) de la semana; null mientras no eligió su Día 1
     * @param planificadas las acciones diarias planificadas de los días que ya llegaron de esa semana (hoy incluido)
     * @param cumplidas    las que de esas completó
     */
    record ObjetivosDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, List<ObjetivoDeLaSemana> objetivos,
                               int planificadas, int cumplidas) {

        public ObjetivosDeLaSemana {
            objetivos = objetivos == null ? List.of() : List.copyOf(objetivos);
        }
    }

    record ObjetivoDeLaSemana(String eje, String titulo) {
    }

    /** La fase (1 a 4) y los días del programa que abarca, estirados al plazo de la persona ({@code CortesDeFase}). */
    record FaseDelPrograma(int numero, int primerDia, int ultimoDia) {

        public FaseDelPrograma {
            if (numero < 1 || primerDia < 1 || ultimoDia < primerDia) {
                throw new IllegalArgumentException("Fase inválida: " + numero + " " + primerDia + "-" + ultimoDia);
            }
        }
    }
}
