package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder.EvaluacionDeMentor;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.SemaforoResumido;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Lo que el Líder ve de un mentor en el padrón, la ficha y el reporte: sus grupos, cuántos aprendices
 * lleva, el semáforo de esos aprendices, cómo atiende sus consultas y su evaluación del mes. Solo
 * cantidades: ningún aprendiz con nombre (decisión del dueño, 2026-10-01).
 *
 * @param grupos     los grupos regulares que lidera hoy; no disponible si la medición de grupos cayó
 * @param aprendices los aprendices activos de esos grupos, sin repetir; null si no se pudo saber
 * @param semaforo   el de esos aprendices juntos; valor null = no lidera ningún grupo hoy
 */
public record IndicadoresDeMentor(UserId mentorId, String nombre, String avatarUrl, Fuente<List<GrupoACargo>> grupos,
                                  Integer aprendices, Fuente<SemaforoResumido> semaforo,
                                  Fuente<AtencionDeTickets> atencion, Fuente<EvaluacionDeMentor> evaluacion) {

    public record GrupoACargo(UUID grupoId, String nombre, int aprendices, SemaforoResumido semaforo) {
    }
}
