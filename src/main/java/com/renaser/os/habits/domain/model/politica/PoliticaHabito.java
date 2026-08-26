package com.renaser.os.habits.domain.model.politica;

import com.renaser.os.habits.domain.model.habito.Habito;

/**
 * Regla propia de un habito del catalogo, resuelta por su {@code claveSistema}.
 *
 * <p><b>Por que existe:</b> de los ~40 habitos del catalogo, solo un punado tiene reglas
 * propias ({@code PHONE_FREE_DAY}, {@code PASTILLA_RENACER}, {@code DAILY_CLASS},
 * {@code GREEN_JUICE}, {@code WARM_LEMON_WATER} en el backend anterior). Sin esta
 * abstraccion, cada uno agrega un {@code if} a {@code RegistroService.completar} — el
 * servicio del hot path que atiende a TODOS. Con ella, agregar un habito con regla nueva
 * es agregar una clase y no tocar nada existente (Open/Closed, CLAUDE.MD §5.4.8).
 *
 * <p><b>NO es sellada, a proposito.</b> El conjunto de habitos con regla propia es
 * abierto — el cliente pide reglas nuevas. Sellarla obligaria a editar el {@code permits}
 * en cada alta, que es exactamente lo que se quiere evitar. Lo que si es sellado es el
 * RESULTADO ({@link DecisionPolitica}), porque ese conjunto si es cerrado.
 *
 * <p><b>Contrato:</b> las implementaciones son funciones puras y sin estado — no hacen
 * I/O, no leen el reloj del sistema y no lanzan excepciones para controlar el flujo.
 * Cualquier dato externo que necesiten llega por parametro.
 *
 * <p><b>Invariante que NINGUNA politica puede romper:</b> el calculo de puntos, la
 * racha, la coherencia y el evento de dominio viven en un solo lugar
 * ({@code RegistroService}). Una politica decide SI una accion procede y por que; nunca
 * reimplementa lo compartido.
 */
public interface PoliticaHabito {

    /**
     * A que habitos atiende esta politica. El registro la indexa por este selector una
     * sola vez al arrancar, asi la resolucion en el hot path es un lookup de mapa y no un
     * recorrido con streams (CLAUDE.MD §5.4.7: nada de streams en el camino de
     * microsegundos).
     */
    SelectorHabito selector();

    /**
     * Si el habito puede darse por cumplido con el gesto generico
     * ({@code POST /habit-tracks/{id}/complete}).
     *
     * <p>Un habito con estado propio responde {@link DecisionPolitica#noProcede} y dice
     * por donde se hace de verdad — es lo que reemplaza al {@code if (esBloqueo())}
     * hardcodeado que habia antes en el servicio.
     */
    DecisionPolitica puedeCompletarseDirecto(Habito habito);
}
