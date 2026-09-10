package com.renaser.os.users.domain.model.mentorprofile;

/**
 * En que se especializa un mentor (tipo `especialidad_mentor`, V48). Las tres que nombro el
 * cliente al cambiar el modelo de agrupacion: el administrador arma el grupo eligiendo mentor, y
 * elige por esto.
 *
 * <p>Es un enum y no un texto libre porque con texto libre "Negocios" y "NEGOCIO" conviven en la
 * misma columna y el filtro al armar el grupo empieza a fallar solo, sin error.
 *
 * <p>Un mentor puede no tenerla: {@code null} = sin declarar todavia, que es como quedaron los
 * perfiles que ya existian cuando entro V48.
 */
public enum EspecialidadMentor {
    NEGOCIO,
    MENTE,
    RELACIONES
}
