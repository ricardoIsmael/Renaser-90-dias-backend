package com.renaser.os.rag.application.ports.out.espejosombra;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto propio de {@code rag} para leer las entradas de diario que alimentan el
 * Espejo Sombra. {@code entradas_diario} es tabla de {@code habits} — por las reglas
 * de Modulith (D-41), {@code rag} no la consulta de frente; el adaptador que
 * implementa este puerto delega en {@code habits.api.EntradaDiarioFinder} (D-50).
 *
 * <p>Deliberadamente NO expone {@code habits.api.EntradaDiarioSummary}: el puerto
 * nombra la intención de negocio de este módulo ("dame texto para analizar"), no la
 * forma del contrato ajeno — así {@code rag.application} no acopla su firma a un tipo
 * de otro módulo. El adaptador ya resuelve, por entrada, cuál es "el mejor texto
 * disponible" (texto propio o transcripción del audio) y descarta las entradas sin
 * contenido, para que el servicio no repita esa lógica.
 *
 * <p><b>Asunción documentada (pregunta abierta en docs/MODULO_RAG.md §6.1):</b> se
 * usan TODAS las entradas de la semana con contenido, sin filtrar por
 * {@code tipo = ESPEJO_SOMBRA} — el esquema no obliga esa restricción y no hay
 * confirmación de negocio de que deba aplicarse. Si el dueño del producto decide
 * filtrar, el cambio es acotado al adaptador de este puerto.
 */
public interface LeerEntradasDiarioPort {

    /**
     * @param inicio primer día incluido
     * @param fin    último día incluido
     * @return solo las entradas con contenido para analizar; lista vacía si no escribió nada en la semana
     */
    List<EntradaDiario> deLaSemana(UserId participanteId, LocalDate inicio, LocalDate fin);

    /**
     * @param fecha día de la entrada original — no se usa para el análisis, solo si algún día hiciera falta ordenar
     * @param texto el mejor texto disponible de la entrada (texto propio o transcripción). Nunca se loguea (CLAUDE.MD §5.4.9)
     */
    record EntradaDiario(LocalDate fecha, String texto) {
    }
}
