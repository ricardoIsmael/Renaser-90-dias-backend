package com.renaser.os.leadership.domain.model.documento;

import java.time.LocalDate;

/**
 * Todo lo que dice un documento, ya reunido y sin la fecha en que se genera (spec 056, FR-035, FR-036 y FR-032): la huella sale de acá, y
 * por eso el instante de la generación no puede formar parte: dos pedidos con los mismos datos darían huellas distintas y nunca se
 * reutilizaría nada (FR-039). La plantilla solo dibuja estos datos; no lee nada más.
 *
 * <p><b>Nunca lleva la nota privada del mentor</b> (FR-037): ningún campo de ningún tipo de documento tiene dónde ponerla.
 */
public sealed interface DatosDelDocumento permits DatosDelAprendiz, DatosDelMentor, DatosDelMes {

    /**
     * Cambia al cambiar la forma del documento: entra a la huella, así un documento guardado con la plantilla anterior se vuelve a
     * generar en vez de reutilizarse.
     *
     * <p>2 (2026-11-18, E2E de la spec 056): las evaluaciones salen enteras («13 %» y no «0.1323 %»), los hábitos del catálogo en tipo oración y los
     * conteos concuerdan en singular («1 consulta»).
     */
    int VERSION_DE_LA_PLANTILLA = 2;

    TipoDeDocumento tipo();

    /** El primer día del período: el sábado de la semana, o el día 1 del mes. */
    LocalDate desde();

    /** El último día del período: el viernes de la semana, o el último día del mes. */
    LocalDate hasta();

    /** La zona de la persona de quien es el documento: en ella se dicen las fechas y la hora de la generación. */
    String zona();
}
