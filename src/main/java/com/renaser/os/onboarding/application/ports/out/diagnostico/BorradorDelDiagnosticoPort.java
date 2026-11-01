package com.renaser.os.onboarding.application.ports.out.diagnostico;

import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El borrador del diagnóstico que Administración edita: las secciones, preguntas y opciones del flujo {@code diagnostico} en las tablas del motor de
 * onboarding. Lo publicado es otra cosa ({@link VersionesDeFichaPort}).
 */
public interface BorradorDelDiagnosticoPort {

    /** Las secciones (sin la oculta de las retiradas) con sus preguntas activas, en orden. */
    FichaDelDiagnostico cargar();

    /** Las preguntas retiradas, de la más reciente a la más antigua. */
    List<PreguntaDelDiagnostico> retiradas();

    Optional<PreguntaDelDiagnostico> pregunta(int id);

    Optional<SeccionDelDiagnostico> seccion(int id);

    SeccionDelDiagnostico crearSeccion(String clave, String titulo, String descripcion);

    void actualizarSeccion(int id, String titulo, String descripcion);

    /** Solo vacía de preguntas activas; las retiradas que tuviera pasan a la sección oculta. */
    void eliminarSeccion(int id);

    void ordenarSecciones(List<Integer> ids);

    /** Inserta ({@code id == 0}) o actualiza la pregunta con sus opciones; devuelve la guardada, con su id. */
    PreguntaDelDiagnostico guardarPregunta(PreguntaDelDiagnostico pregunta);

    /** La deja en {@code seccionId}, en la {@code posicion} (0 = primera) entre las activas de esa sección. */
    void moverPregunta(int id, int seccionId, int posicion);

    void eliminarPreguntas(Collection<Integer> ids);

    void retirarPreguntas(Collection<Integer> ids, Instant ahora);

    void restaurarPregunta(int id, int seccionId);

    /** Le añade a la clave de estas preguntas su id, para que otra pregunta nueva pueda usar la clave (el reemplazo de la ficha por un archivo). */
    void liberarClaves(Collection<Integer> ids);

    /** Cuántas personas respondieron cada pregunta del diagnóstico (solo las que tienen al menos una). */
    Map<Integer, Integer> respuestasPorPregunta();

    /** De estas preguntas, las que alguna versión publicada incluyó. */
    Set<Integer> publicadasAlgunaVez(Collection<Integer> ids);
}
