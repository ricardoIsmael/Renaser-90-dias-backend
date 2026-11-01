package com.renaser.os.onboarding.application.ports.in.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.EscalaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Set;

/**
 * Crear, cambiar, mover, duplicar, quitar y restaurar secciones y preguntas del borrador del diagnóstico (spec 026, US5 y US7). Solo Administración y
 * Alquimista. Lo que se edita es el borrador: las personas siguen viendo lo último publicado.
 */
public interface EditarDiagnosticoUseCase {

    SeccionDelDiagnostico crearSeccion(UserId actor, String titulo, String descripcion);

    SeccionDelDiagnostico actualizarSeccion(UserId actor, int id, String titulo, String descripcion);

    /** Quita sus preguntas con las reglas de {@link #quitar} y la borra. */
    ResultadoDeQuitar eliminarSeccion(UserId actor, int id, boolean quitarDependientes);

    /** {@code ids}: todas las secciones, en el orden nuevo. */
    void ordenarSecciones(UserId actor, List<Integer> ids);

    PreguntaAdministrada crearPregunta(UserId actor, PreguntaPedida pedida);

    PreguntaAdministrada actualizarPregunta(UserId actor, int id, PreguntaPedida pedida);

    void moverPregunta(UserId actor, int id, int seccionId, int posicion);

    PreguntaAdministrada duplicarPregunta(UserId actor, int id);

    ResultadoDeQuitar quitar(UserId actor, List<Integer> ids, boolean quitarDependientes);

    PreguntaAdministrada restaurar(UserId actor, int id);

    /** Lo que Administración manda para crear o cambiar una pregunta. */
    record PreguntaPedida(int seccionId, String texto, String ayuda, TipoPreguntaOnboarding tipo, boolean requerida, Integer maximoOpciones,
                          List<OpcionDelDiagnostico> opciones, EscalaDelDiagnostico escala, CondicionDeVisibilidad condicion,
                          DestinoDeRespuesta alimenta, Set<Formacion> formaciones, String precarga) {
    }

    /** Una pregunta con lo que Administración necesita saber de ella: cuántas personas la respondieron y si la usa otra función. */
    record PreguntaAdministrada(PreguntaDelDiagnostico pregunta, int respuestas, boolean protegida) {
    }

    /** {@code eliminadas}: borradas del todo; {@code retiradas}: dejaron de mostrarse pero conservan sus respuestas; {@code conRespuestas}: de las quitadas, las que tenían. */
    record ResultadoDeQuitar(int eliminadas, int retiradas, int conRespuestas) {
    }
}
