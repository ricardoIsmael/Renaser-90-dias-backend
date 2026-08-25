package com.renaser.os.onboarding.application.ports.out.cuestionario;

import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;

import java.util.List;
import java.util.Optional;

/**
 * Lectura del catalogo (secciones/preguntas/opciones). Solo lectura en este alcance: no
 * hay seeds ni CRUD admin todavia (decision de alcance, ver docs/MODULO_ONBOARDING.md) —
 * las tablas quedan vacias hasta la fase futura de migracion de datos.
 */
public interface LoadCuestionarioPort {

    /** Ordenadas por `orden`. */
    List<Seccion> seccionesDeFlujo(String flujo);

    /** Ordenadas por `orden`. */
    List<Pregunta> preguntasDeSeccion(short seccionId);

    /** Ordenadas por `orden`. */
    List<OpcionPregunta> opcionesDePregunta(int preguntaId);

    Optional<Pregunta> porId(int preguntaId);
}
