package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;

/**
 * Anota en {@code consultas_de_ficha} que alguien abrió el diagnóstico de una persona (spec 026, decisión del dueño). Lo dispara el evento que publica
 * {@code onboarding} al leerlo. Idempotente por {@code consultaId}: el outbox puede entregarlo más de una vez.
 */
public interface RegistrarLecturaDelDiagnosticoUseCase {

    void registrar(DiagnosticoConsultadoEvent lectura);
}
