package com.renaser.os.rag.application.ports.in.espejosombra;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * Genera (si corresponde) el informe semanal del Espejo Sombra de un participante.
 *
 * <p><b>NO se expone por REST</b> (docs/MODULO_RAG.md §4, D-47): la única forma de
 * invocarlo es {@code GenerarInformesSemanalesScheduler}. Un aprendiz o un mentor no
 * pueden "pedir" un informe a demanda — nace del barrido semanal.
 *
 * <p><b>Idempotente</b>: si ya existe un informe para {@code (participanteId, semanaInicio)}
 * — garantizado además por el UNIQUE de {@code informes_espejo_sombra} — no hace
 * nada. Tampoco genera nada si la semana no tuvo ninguna entrada de diario con
 * contenido, o si la IA no está disponible. Ver el javadoc de
 * {@code EspejoSombraService.generar} para el detalle de cada camino.
 */
public interface GenerarInformeEspejoSombraUseCase {

    void generar(UserId participanteId, LocalDate semanaInicio);
}
