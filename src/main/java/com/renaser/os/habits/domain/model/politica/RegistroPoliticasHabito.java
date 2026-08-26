package com.renaser.os.habits.domain.model.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.TipoHabito;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexa las politicas por su selector UNA SOLA VEZ, al construirse.
 *
 * <p>El motivo es de rendimiento y esta explicito en CLAUDE.MD §5.4.7: resolver la
 * politica con {@code politicas.stream().filter(...).findFirst()} en cada completacion
 * asignaria objetos intermedios en un hot path que corre cada vez que alguien marca un
 * habito. Aca el recorrido ocurre una vez en el arranque y despues cada resolucion son
 * uno o dos {@code Map.get} sin asignacion.
 *
 * <p><b>Orden de especificidad</b> (de mas especifico a mas general), decidido asi
 * porque una regla para UN habito puntual siempre debe poder ganarle a una regla para
 * TODA una forma de habito:
 *
 * <ol>
 *   <li>por {@code claveSistema} — el habito puntual del catalogo</li>
 *   <li>por {@code tipo} — la forma estructural (ej. todo BLOQUEO es Santuario)</li>
 *   <li>{@link #GENERICA} — sin regla propia, se completa con el gesto generico</li>
 * </ol>
 *
 * <p>Los habitos PERSONALES no tienen {@code claveSistema}, asi que caen directo al paso
 * 2 o 3 — lo correcto: un habito que se invento un participante no puede traer una regla
 * de negocio del catalogo.
 */
public final class RegistroPoliticasHabito {

    /** Los habitos sin regla propia se completan con el gesto generico, sin condiciones. */
    public static final PoliticaHabito GENERICA = new PoliticaHabito() {
        @Override
        public SelectorHabito selector() {
            throw new UnsupportedOperationException("La politica generica es el fallback: no se indexa");
        }

        @Override
        public DecisionPolitica puedeCompletarseDirecto(Habito habito) {
            return DecisionPolitica.procede();
        }
    };

    private final Map<String, PoliticaHabito> porClave;
    private final Map<TipoHabito, PoliticaHabito> porTipo;

    public RegistroPoliticasHabito(List<PoliticaHabito> politicas) {
        Map<String, PoliticaHabito> indiceClave = new HashMap<>();
        Map<TipoHabito, PoliticaHabito> indiceTipo = new EnumMap<>(TipoHabito.class);
        for (PoliticaHabito politica : politicas) {
            // El switch sobre el selector sellado: el compilador obliga a cubrir ambas formas.
            switch (politica.selector()) {
                case SelectorHabito.PorClaveSistema(String clave) ->
                        registrar(indiceClave, clave, politica, "claveSistema");
                case SelectorHabito.PorTipo(TipoHabito tipo) ->
                        registrar(indiceTipo, tipo, politica, "tipo");
            }
        }
        this.porClave = Map.copyOf(indiceClave);
        this.porTipo = Map.copyOf(indiceTipo);
    }

    /**
     * Dos politicas para el mismo selector es una ambiguedad silenciosa: cual gana
     * dependeria del orden en que Spring descubra los beans. Se falla al arrancar, que es
     * barato, en vez de en produccion con la politica equivocada aplicada.
     */
    private static <K> void registrar(Map<K, PoliticaHabito> indice, K clave, PoliticaHabito politica,
                                       String dimension) {
        PoliticaHabito previa = indice.put(clave, politica);
        if (previa != null) {
            throw new IllegalStateException("Dos politicas declaran el mismo " + dimension + " '" + clave
                    + "': " + previa.getClass().getName() + " y " + politica.getClass().getName());
        }
    }

    /** Nunca devuelve null: sin regla propia, {@link #GENERICA}. */
    public PoliticaHabito para(Habito habito) {
        String clave = habito.claveSistema();
        if (clave != null) {
            PoliticaHabito porClaveSistema = porClave.get(clave);
            if (porClaveSistema != null) {
                return porClaveSistema;
            }
        }
        return porTipo.getOrDefault(habito.tipo(), GENERICA);
    }
}
