package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDeNoventaDias;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Los objetivos de una persona, solo para leer, en la ficha del líder (spec 056, FR-027): los de los 90 días, el del mes en curso y
 * los de la semana. Salen de {@code rocks.api.RocasDelAprendizFinder}, el mismo contrato con el que el acompañante lee las rocas de un
 * aprendiz: no hay una regla nueva ni un cálculo propio, y nada se puede escribir desde acá.
 */
public interface ConsultarObjetivosDeLaPersonaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SIN_PROGRAMA} (404) si es un mentor o una guía que no sigue su programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    si quien pide no es del liderazgo o su cuenta está suspendida
     * @throws java.util.NoSuchElementException                       (404) si la persona no existe, no se muestra o es de otra formación
     */
    ObjetivosDeLaPersona objetivosDe(ConsultaObjetivosDeLaPersona consulta);

    record ConsultaObjetivosDeLaPersona(UserId actorId, UUID personaId) {

        public ConsultaObjetivosDeLaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    /**
     * @param noventaDias uno por eje con Roca Maestra definida, por eje; vacío si todavía no los definió
     * @param mes         el mes en curso; null si no hay objetivos del mes (programa sin arrancar o sin objetivos)
     * @param semana      la semana de programa en curso; null si no tiene objetivos semanales
     */
    record ObjetivosDeLaPersona(List<ObjetivoDeNoventaDias> noventaDias, ObjetivosDelMes mes, ObjetivosDeLaSemana semana) {

        public ObjetivosDeLaPersona {
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
}
