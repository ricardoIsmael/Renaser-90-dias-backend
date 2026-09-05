package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

/**
 * Santuario: un habito de BLOQUEO no se marca hecho de un tirón — tiene una sesion con
 * su propio ciclo (iniciar / completar / romper) que puede cruzar la medianoche, y ese
 * estado vive en su propia tabla.
 *
 * <p>Esta clase reemplaza al {@code if (habito.esBloqueo()) throw ...} que estaba
 * hardcodeado dentro de {@code RegistroService.completar}. El comportamiento observable
 * es identico; lo que cambia es que agregar el proximo habito con regla propia ya no
 * toca ese servicio.
 *
 * <p>Selecciona por TIPO y no por clave: cualquier habito de bloqueo necesita una sesion,
 * sea el "Dia sin celular" o uno que el cliente agregue manana.
 */
@Component
public class PoliticaSantuario implements PoliticaHabito {

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porTipo(TipoHabito.BLOQUEO);
    }

    /** Ignora el {@code contexto}: para saber que un BLOQUEO necesita sesion alcanza el habito. */
    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        return DecisionPolitica.noProcede(
                "Los habitos BLOQUEO (Santuario) se completan via /habit-tracks/{id}/santuario, no aca");
    }
}
