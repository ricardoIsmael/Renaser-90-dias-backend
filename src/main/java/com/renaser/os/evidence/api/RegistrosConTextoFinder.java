package com.renaser.os.evidence.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * "De estos registros de hábito, cuáles ya tienen una evidencia de TEXTO con al menos tantos caracteres."
 *
 * <p><b>Por qué existe (E-652, issue #33).</b> La Audioterapia semanal se cierra con sus dos respuestas, que la app
 * sube como una evidencia de TEXTO antes de completar el registro. {@link RegistrosConEvidenciaFinder} no alcanza
 * para exigirlo: responde «subió algo», y una foto o un audio también cuentan. Aquí la pregunta es «subió las
 * respuestas», que es una evidencia de tipo TEXTO con un largo mínimo. El largo lo manda quien pregunta: este puerto
 * no sabe de preguntas ni de hábitos.
 *
 * <p>En lote y sin {@code actorId}, igual que su hermano: quien llama ya autorizó. Se apoya en
 * {@code evidencias_registro_idx}.
 */
public interface RegistrosConTextoFinder {

    /**
     * El subconjunto de {@code registrosHabitoIds} con al menos una evidencia de tipo TEXTO cuyo contenido, sin los
     * espacios de los bordes, mide {@code largoMinimo} caracteres o más. En cualquier estado de validación, como el
     * finder hermano: la pregunta es «ya escribió», no «ya aprobó».
     *
     * @return conjunto vacío si la colección viene vacía o ninguno cumple; nunca null
     */
    Set<UUID> conTextoDeAlMenos(Collection<UUID> registrosHabitoIds, int largoMinimo);
}
