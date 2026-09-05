package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

/**
 * POST DIARIO EN COMUNIDAD: no se marca hecho con el gesto generico. Se da por cumplido
 * solo si el aprendiz publico de verdad en el Muro ese dia.
 *
 * <p>Pedido del dueno del producto (2026-09-04), textual: <i>"Cuando publique algo, y recien
 * ahi, se marca como completado. Ojo: debe publicar algo para poder comprobar el estado."</i>
 * Esa segunda frase es la que decide DONDE vive la regla: la comprobacion la hace el
 * servidor contra `publicaciones_muro`, no el cliente diciendo que publico. Un telefono que
 * llame a {@code POST /habit-tracks/{id}/complete} sin haber publicado recibe un 400,
 * mande lo que mande en el body.
 *
 * <p><b>Selecciona por clave y no por tipo</b>, al reves que {@link PoliticaSantuario}: la
 * regla es de ESTE habito. Su tipo es {@code CHECKBOX}, que comparte con otros quince del
 * catalogo a los que nadie les pidio exigir una publicacion. La clave se la puso
 * {@code V24__habito_post_diario_clave_sistema.sql}; sin esa migracion esta politica no
 * selecciona a nadie y el habito se sigue completando como antes.
 *
 * <p><b>Que NO decide esta clase</b> (invariante de {@link PoliticaHabito}): cuantos puntos
 * vale, si expiro, la racha ni el evento de dominio. Todo eso lo sigue calculando
 * {@code RegistroService} en un solo lugar — esta politica solo responde si la accion
 * procede. En particular, el habito sigue valiendo los mismos 10 puntos de
 * {@code ResultadoOtorgamiento.PUNTOS_COMPLETOS} dentro de su ventana (disparo 22:00): la
 * escala de puntaje no se toca.
 */
@Component
public class PoliticaPostDiarioComunidad implements PoliticaHabito {

    /**
     * La misma cadena que la fila ya llevaba en `icono_clave` y que V24 copio a
     * `clave_sistema`. Publica para que una prueba pueda referirse a ella sin retipearla.
     */
    public static final String CLAVE_SISTEMA = "COMMUNITY_POST";

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porClave(CLAVE_SISTEMA);
    }

    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        if (contexto.publicoEnElMuroEseDia()) {
            return DecisionPolitica.procede();
        }
        // El motivo lo lee una persona en el telefono: dice que falta Y por donde se hace.
        return DecisionPolitica.noProcede(
                "Publica algo en el Muro de la comunidad para dar por cumplido este habito");
    }
}
