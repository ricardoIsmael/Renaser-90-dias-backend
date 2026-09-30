package com.renaser.os.rag.domain.model.mapa;

import java.util.List;
import java.util.Optional;

/**
 * El proximo hito del Mapa de Renacimiento segun el dia del programa de hoy (D-233).
 *
 * <p>Los hitos del Mapa son tres y fijos: dias 30, 60 y 90 (preguntas {@code map_milestone_*_30/60/90}
 * de la V41). El proximo es el primero que todavia no paso: el mismo dia 30 cuenta como "es hoy".
 *
 * <p>Recibe el dia ya derivado de las fechas en la zona de la persona ({@code users} lo deriva de
 * {@code fecha_inicio}, su zona y los ajustes, regla 02): aca no se cuenta nada a mano ni se lee un reloj.
 *
 * @param dia    30, 60 o 90
 * @param faltan dias que faltan desde hoy; 0 = es hoy
 */
public record ProximoHito(int dia, int faltan) {

    public static final List<Integer> DIAS_DE_HITO = List.of(30, 60, 90);

    /**
     * Vacio antes del dia 1 (el programa no arranco: contar desde ahi daria un numero falso) y despues
     * del dia 90 (ya no queda ninguno).
     */
    public static Optional<ProximoHito> para(int diaDeHoy) {
        if (diaDeHoy < 1) {
            return Optional.empty();
        }
        return DIAS_DE_HITO.stream()
                .filter(dia -> dia >= diaDeHoy)
                .findFirst()
                .map(dia -> new ProximoHito(dia, dia - diaDeHoy));
    }
}
