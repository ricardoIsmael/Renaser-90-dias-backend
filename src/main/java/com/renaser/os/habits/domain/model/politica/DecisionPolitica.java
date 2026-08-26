package com.renaser.os.habits.domain.model.politica;

/**
 * Resultado de consultarle a la politica de un habito si una accion procede.
 *
 * <p>Sellada A PROPOSITO, a diferencia de {@code PoliticaHabito}: el conjunto de
 * resultados posibles SI es cerrado (procede o no procede), asi que el compilador puede
 * y debe obligar a cubrir ambos casos en el pattern matching. Es el mismo criterio de
 * CLAUDE.MD §5.4.7 y §5.3.4 ({@code AccessDecision}).
 *
 * <p>Devolver un VALOR en vez de lanzar una excepcion mantiene a las politicas como
 * funciones puras: entran datos, sale una decision, sin efectos ni control de flujo por
 * excepcion. Quien orquesta decide como traducir un rechazo (hoy, a un 400).
 */
public sealed interface DecisionPolitica permits DecisionPolitica.Procede, DecisionPolitica.NoProcede {

    record Procede() implements DecisionPolitica {
    }

    /** @param motivo texto para el cliente — explica por que no procede y, cuando aplica,
     *                por donde SI se hace (ej. el gesto propio de ese habito). */
    record NoProcede(String motivo) implements DecisionPolitica {
    }

    DecisionPolitica PROCEDE = new Procede();

    static DecisionPolitica procede() {
        return PROCEDE;
    }

    static DecisionPolitica noProcede(String motivo) {
        return new NoProcede(motivo);
    }
}
