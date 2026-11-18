package com.renaser.os.leadership.domain.model.documento;

/**
 * De quién es el documento (spec 056, FR-035, FR-036 y FR-032): la semana de un aprendiz, la semana de un mentor o el reporte del mes
 * del cuerpo de mentores. El nombre viaja tal cual a la base ({@code documentos_semanales.tipo}) y a la ruta del archivo.
 */
public enum TipoDeDocumento {
    APRENDIZ,
    MENTOR,
    MES
}
