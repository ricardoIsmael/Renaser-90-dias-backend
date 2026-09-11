package com.renaser.os.mentoring.domain.model.aviso;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cuándo hay que avisarle al mentor. Puras: reciben las obligaciones ya leídas y el día local
 * ya resuelto, y devuelven qué avisos corresponden.
 *
 * <p>Lo que estas reglas NO hacen es tan importante como lo que hacen: no avisan por días
 * futuros, no avisan de alguien sin obligaciones registradas —porque no saber es distinto de
 * incumplir—, y no avisan de una evidencia antes de que venza (plan.md §9).
 */
public final class ReglasDeAviso {

    private ReglasDeAviso() {
    }

    /**
     * Una obligación ya recortada a lo que la regla necesita.
     *
     * @param entregada si tiene entrega acreditable. {@code null} en {@code ultimaActividad}
     *                  significa "nunca registró nada", no "hace cero días".
     */
    public record ObligacionEvaluada(LocalDate fecha, boolean requiereEvidencia, boolean exigible,
                                      boolean cumplida, boolean entregada) {
    }

    /**
     * @param hoyLocal          día actual en la zona del alumno, ya resuelto por quien llama.
     * @param umbralSinActividad días completos sin actividad antes de avisar (P-06).
     */
    public static List<AvisoDeAcompanamiento> evaluar(UserId mentorId, UserId alumnoId, UUID grupoId,
                                                       List<ObligacionEvaluada> obligaciones, LocalDate hoyLocal,
                                                       int umbralSinActividad) {
        List<AvisoDeAcompanamiento> avisos = new ArrayList<>();
        if (obligaciones.isEmpty()) {
            // Sin una sola obligación registrada no se puede afirmar nada de esta persona.
            // Avisar acá seria inventar un incumplimiento a partir de la falta de datos.
            return avisos;
        }

        sinActividad(mentorId, alumnoId, grupoId, obligaciones, hoyLocal, umbralSinActividad).ifPresent(avisos::add);
        evidenciaVencida(mentorId, alumnoId, grupoId, obligaciones, hoyLocal).ifPresent(avisos::add);
        return avisos;
    }

    private static java.util.Optional<AvisoDeAcompanamiento> sinActividad(
            UserId mentorId, UserId alumnoId, UUID grupoId, List<ObligacionEvaluada> obligaciones,
            LocalDate hoyLocal, int umbral) {

        LocalDate ultimaActividad = obligaciones.stream()
                .filter(ObligacionEvaluada::cumplida)
                .map(ObligacionEvaluada::fecha)
                .filter(f -> !f.isAfter(hoyLocal))
                .max(Comparator.naturalOrder())
                .orElse(null);

        // Sin ninguna actividad, el reloj cuenta desde su obligación más antigua conocida: es la
        // primera fecha en la que se sabe que ya estaba en el programa.
        LocalDate desde = ultimaActividad != null ? ultimaActividad : obligaciones.stream()
                .map(ObligacionEvaluada::fecha)
                .filter(f -> !f.isAfter(hoyLocal))
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (desde == null) {
            // Todas sus obligaciones son futuras: recién empieza. No hay nada que reprochar.
            return java.util.Optional.empty();
        }

        long diasCompletos = java.time.temporal.ChronoUnit.DAYS.between(desde, hoyLocal);
        if (diasCompletos < umbral) {
            return java.util.Optional.empty();
        }
        // El ancla es la última actividad: mientras siga sin registrar nada no se mueve, y el
        // aviso no se repite. Cuando vuelva y se ausente otra vez, será otro episodio.
        return java.util.Optional.of(new AvisoDeAcompanamiento(mentorId, alumnoId, grupoId,
                MotivoAviso.SIN_ACTIVIDAD, desde, (int) diasCompletos));
    }

    private static java.util.Optional<AvisoDeAcompanamiento> evidenciaVencida(
            UserId mentorId, UserId alumnoId, UUID grupoId, List<ObligacionEvaluada> obligaciones,
            LocalDate hoyLocal) {

        List<ObligacionEvaluada> vencidas = obligaciones.stream()
                .filter(ObligacionEvaluada::exigible)
                .filter(o -> !o.entregada())
                // Estrictamente anterior a hoy: una obligación de hoy todavía tiene el día por
                // delante, y avisar de ella seria apurar a alguien que está a tiempo.
                .filter(o -> o.fecha().isBefore(hoyLocal))
                .sorted(Comparator.comparing(ObligacionEvaluada::fecha))
                .toList();

        if (vencidas.isEmpty()) {
            return java.util.Optional.empty();
        }
        // Ancla en la más antigua sin resolver: el aviso se repite recién cuando esa se resuelve
        // y pasa a ser otra la más vieja.
        return java.util.Optional.of(new AvisoDeAcompanamiento(mentorId, alumnoId, grupoId,
                MotivoAviso.EVIDENCIA_VENCIDA, vencidas.getFirst().fecha(), vencidas.size()));
    }
}
