package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La semana de un alumno, vista por quien lo acompaña. */
public interface ConsultarSeguimientoSemanalUseCase {

    SemanaDelAlumno semanaDe(ConsultaSemana consulta);

    record ConsultaSemana(UserId actorId, UUID grupoId, UUID alumnoId, LocalDate inicioDeSemana) {
    }

    /**
     * @param cobertura de datos. Que falte información no es lo mismo que incumplir: hay tres
     *                  estados, no dos (plan.md §7).
     */
    record SemanaDelAlumno(UUID grupoId, UUID alumnoId, String nombre, LocalDate inicioDeSemana,
                            LocalDate finDeSemana, Integer diaDePrograma, String zona,
                            List<DiaDelAlumno> dias, ResumenSemana resumen, String cobertura,
                            Instant actualizadoEn) {
    }

    record DiaDelAlumno(LocalDate fecha, Integer diaDePrograma, List<ObligacionDelDia> obligaciones) {
    }

    /**
     * @param estadoHabito     PENDIENTE / EN_CURSO / COMPLETADO / FALLIDO / EXPIRADO.
     * @param entrega          NO_REQUERIDA / SIN_ENTREGA / ENTREGADA. Separado del hábito porque
     *                         cumplir y entregar son dos hechos distintos.
     * @param revision         null cuando no hay entrega. Nunca se convierte en "rechazada".
     */
    record ObligacionDelDia(UUID registroId, String titulo, String estadoHabito, boolean requiereEvidencia,
                             String entrega, Instant entregadaEn, String revision, UUID evidenciaId) {
    }

    /**
     * Números simples, no un porcentaje de calificación: la nota del mentor es mensual y la
     * calcula otro motor. Acá se cuenta lo que pasó esta semana.
     */
    record ResumenSemana(int obligaciones, int cumplidas, int conEntrega, int pendientes, int sinCumplir) {
    }
}
