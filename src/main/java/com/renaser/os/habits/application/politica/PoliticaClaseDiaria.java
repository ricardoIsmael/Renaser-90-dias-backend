package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.politica.PoliticaHabito;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.springframework.stereotype.Component;

/**
 * CLASE DIARIA: no se marca hecha con el gesto generico. Se cierra unicamente entregando el
 * resumen, por {@code POST /api/v1/classroom/clase-diaria}.
 *
 * <p><b>Por que existe</b> (E-120). El contrato publicado
 * ({@code docs/api/CONTRATO_CONTENIDO_IA.md} §1.11-bis) afirma, textual: <i>"Sin este POST el
 * habito NO queda completado: no hay estado intermedio 'completado sin resumen'"</i>. El codigo
 * decia lo contrario: {@code DAILY_CLASS} es un {@code CHECKBOX} sin politica propia, asi que
 * caia en {@code RegistroPoliticasHabito.GENERICA} y se podia cerrar por dos caminos que no
 * piden resumen — {@code POST /habit-tracks/&#123;id&#125;/complete} y la herramienta
 * {@code marcar_habito_completado} del agente de {@code rag}. Cerrado por cualquiera de los dos,
 * el aprendiz perdia la clase del dia: el registro quedaba COMPLETADO (estado terminal) y el
 * resumen que escribiera despues ya no tenia donde entrar.
 *
 * <p><b>Selecciona por clave y no por tipo</b>, igual que {@link PoliticaPostDiarioComunidad}:
 * la regla es de ESTE habito. Su tipo es {@code CHECKBOX}, que comparte con otros quince del
 * catalogo a los que nadie les pidio exigir un resumen. La clave {@code DAILY_CLASS} ya viene
 * del baseline ({@code V4__catalogo_habitos_default.sql}), asi que esta politica selecciona
 * desde el primer arranque.
 *
 * <p><b>Por que esto NO rompe el camino legitimo.</b> {@code ClaseDiariaHabitoService} pasa a
 * proposito por {@code CompletarRegistroUseCase} para no duplicar el calculo de puntos ni el de
 * la ventana de entrega, asi que tambien atraviesa esta politica. Lo que lo distingue es el
 * {@link GestoCompletar#PROPIO_DEL_HABITO} que lleva su comando: {@code RegistroService} solo
 * consulta la politica cuando el gesto es el generico, que es la unica pregunta que
 * {@link PoliticaHabito#puedeCompletarseDirecto} dice contestar.
 *
 * <p><b>Que NO decide esta clase</b> (invariante de {@link PoliticaHabito}): cuantos puntos vale,
 * si expiro, la racha ni el evento de dominio. Todo eso lo sigue calculando
 * {@code RegistroService} en un solo lugar.
 */
@Component
public class PoliticaClaseDiaria implements PoliticaHabito {

    @Override
    public SelectorHabito selector() {
        return SelectorHabito.porClave(CompletarClaseDiariaHabitoUseCase.CLAVE_SISTEMA_DAILY_CLASS);
    }

    /** Ignora el {@code contexto}: no hay hecho externo que mirar — este habito nunca se cierra de un tiron. */
    @Override
    public DecisionPolitica puedeCompletarseDirecto(Habito habito, ContextoCompletar contexto) {
        // El motivo lo lee una persona en el telefono: dice que falta Y por donde SI se hace.
        return DecisionPolitica.noProcede(
                "La Clase Diaria se da por cumplida al enviar el resumen de la clase, desde Training");
    }
}
