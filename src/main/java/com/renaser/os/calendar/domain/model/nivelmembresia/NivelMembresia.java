package com.renaser.os.calendar.domain.model.nivelmembresia;

/**
 * Catalogo {@code niveles_membresia}. El nivel de un visor NUNCA se guarda en su fila de
 * usuario — se DERIVA en el momento de {@code programDia/90} (ver {@link ProgresoNivel}),
 * igual que el repo viejo (resolveLevelRank, audience.ts).
 */
public record NivelMembresia(int id, int rango, String nombre, int pctProgresoMinimo) {

    public NivelMembresia {
        if (pctProgresoMinimo < 0 || pctProgresoMinimo > 100) {
            throw new IllegalArgumentException("pctProgresoMinimo debe estar entre 0 y 100");
        }
    }
}
