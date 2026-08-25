package com.renaser.os.calendar.domain.model.evento;

/**
 * Tipo Postgres {@code tipo_evento_calendario}. A diferencia de otros enums de este
 * modulo, sus literales son IDENTICOS en dominio, base de datos y wire REST — el codigo
 * viejo (eventTypes.ts) ya los nombraba en español y la app movil los consume tal cual.
 * Sin traduccion en ningun borde.
 *
 * <p>Cada tipo trae sus recordatorios por defecto y si exige elegibilidad especial — ver
 * {@link ReglasPorTipoEvento}, la UNICA fuente de esas reglas.
 */
public enum TipoEvento {

    MENTORIA_ALQUIMISTA,
    ESPONTANEO,
    SEMANA_MANIFESTACION,
    SESION_ESPECIAL
}
