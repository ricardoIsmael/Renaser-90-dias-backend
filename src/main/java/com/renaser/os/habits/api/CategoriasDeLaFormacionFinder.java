package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Las categorias de habito (las dimensiones de Training) de la formacion de UNA persona, vistas desde otro modulo (spec 038).
 *
 * <p>Primer consumidor: el acompanante de {@code rag} (SER), que antes solo conocia las cuatro categorias de la Escuela (Cuerpo, Mente,
 * Emociones, Espiritu) y por eso rotulaba «Sin dimension» a un habito del Diplomado o la Certificacion, o proponia crear un habito propio en una
 * categoria que esa formacion no tiene (y que el alta rechaza porque un habito tiene que estar en una categoria de SU formacion).
 *
 * <p>No decide nada: es la misma lectura que sirve a Training ({@code ConsultarCategoriasDeMiFormacionUseCase}), con la formacion que
 * decide el servidor para esa persona. Sin formacion (personal sin asignar) devuelve la lista vacia, no un error.
 */
public interface CategoriasDeLaFormacionFinder {

    /** Las categorias ACTIVAS de la formacion de la persona, en el orden que Administracion les dio. Nunca {@code null}. */
    List<CategoriaResumen> activasDe(UserId participanteId);

    /** @param clave la llave de {@code categorias_habito} ({@code CUERPO}, {@code DIPLOMADO_ESTUDIO}...); @param nombre como la ve la persona */
    record CategoriaResumen(String clave, String nombre) {
    }
}
