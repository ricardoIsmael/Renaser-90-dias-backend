package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Ranking mensual entre los grupos de una cohorte, por cumplimiento de evidencias.
 *
 * <p>No sustituye ni reinterpreta los rankings de puntos que ya existen: es otra pregunta y
 * otra tabla de origen. Los de personas siguen intactos (P-08, contracts.md).
 */
public interface ConsultarRankingDeGruposUseCase {

    RankingDeGrupos ranking(UserId actorId, UUID cohorteId, YearMonth mes);

    record RankingDeGrupos(UUID cohorteId, String mes, String zona, String versionFormula,
                            List<FilaDeGrupo> grupos) {
    }

    /**
     * @param posicion  los empates COMPARTEN posición y la siguiente salta (1, 2, 2, 4). Ordena
     *                  por el valor sin redondear; el redondeo es solo de presentación.
     * @param porcentaje {@code null} cuando el grupo no tiene muestra. No es cero.
     * @param muestra   alumnos que entraron al promedio. Un grupo con 2 evaluables no compara
     *                  igual que uno con 10, y ocultarlo haría parecer comparables dos números
     *                  que no lo son.
     */
    record FilaDeGrupo(int posicion, UUID grupoId, String nombre, BigDecimal porcentaje, int muestra,
                        int entregadas, int esperadas, String estado) {
    }
}
