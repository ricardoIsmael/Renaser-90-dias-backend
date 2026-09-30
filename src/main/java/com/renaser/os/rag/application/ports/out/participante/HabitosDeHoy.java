package com.renaser.os.rag.application.ports.out.participante;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Como estaba el dia de habitos de la persona al empezar el turno: los de hoy con su estado, y los
 * pausados (D-176). Viaja dentro de {@link ConsultarSituacionDelAprendizPort.SituacionDelAprendiz}
 * y entra al prompt del acompanante en cada turno.
 *
 * <p><b>Sin identificadores, a proposito.</b> El prompt le prohibe al modelo mostrar ids internos
 * (E-270), y esto va en cada turno: cuanto mas corto, mejor. Para ACTUAR sobre un habito el modelo
 * sigue pidiendo los ids a las herramientas, igual que antes.
 *
 * @param deHoy    los habitos que le tocan hoy, en el orden de la agenda
 * @param pausados los que no se le piden hoy porque estan pausados
 */
public record HabitosDeHoy(List<HabitoDeHoy> deHoy, List<HabitoPausado> pausados) {

    public HabitosDeHoy {
        deHoy = List.copyOf(Objects.requireNonNull(deHoy, "deHoy es obligatorio"));
        pausados = List.copyOf(Objects.requireNonNull(pausados, "pausados es obligatorio"));
    }

    /**
     * @param titulo            el que ve la persona (la agenda ya aplica sus renombres, D-133)
     * @param pideFoto          si se registra con la camara de la app (D-171)
     * @param tituloDelPrograma el del catalogo cuando la persona lo renombro, {@code null} si no
     *                          (E-290): para que el modelo una "jugo verde" con "Batido de papaya"
     */
    public record HabitoDeHoy(String titulo, EstadoDeHoy estado, boolean pideFoto, String tituloDelPrograma) {

        public HabitoDeHoy {
            Objects.requireNonNull(titulo, "titulo es obligatorio");
            Objects.requireNonNull(estado, "estado es obligatorio");
        }

        /** Un habito sin renombre. */
        public HabitoDeHoy(String titulo, EstadoDeHoy estado, boolean pideFoto) {
            this(titulo, estado, pideFoto, null);
        }
    }

    /** @param hasta ultimo dia de la pausa, o {@code null} si no tiene fecha de fin */
    public record HabitoPausado(String titulo, LocalDate hasta) {

        public HabitoPausado {
            Objects.requireNonNull(titulo, "titulo es obligatorio");
        }
    }

    /**
     * El estado en palabras de la persona, no el de {@code registros_habito}: {@code VENCIDO} junta
     * un registro EXPIRADO y uno PENDIENTE al que ya se le paso el plazo, que para ella son lo mismo
     * (se le paso la hora). Lo decide {@code SituacionDelTurnoService}, no el modelo.
     *
     * <p>E-455: {@code VENCIDO} quiere decir "ya no da puntos", no que el habito vencio: se puede
     * completar igual (paga 0). Al modelo se le dice asi, nunca "vencido".
     */
    public enum EstadoDeHoy {
        PENDIENTE, EN_CURSO, HECHO, VENCIDO, FALLIDO
    }
}
