package com.renaser.os.onboarding;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los términos en las pruebas de integración (spec 027). La base de las pruebas de edición es la que dejó la V149: publicada la versión 1 de la Escuela y un BORRADOR en
 * el Diplomado y la Certificación (sin publicar: esas formaciones no reciben a nadie hasta publicarlos). La V162 (spec 039, el Acuerdo de transformación) publica
 * encima el texto nuevo; {@link #restablecer} la deshace a propósito para que estas pruebas partan de un estado conocido, y la prueba de la V162
 * ({@code AcuerdoDeTransformacionIT}) vuelve a correr esa migración sobre ese estado. Las pruebas que aceptan gente en esas formaciones los publican con {@link #publicarEnTodas}
 * y los dejan como estaban con {@link #restablecer}. Una versión publicada no se puede borrar (la base lo impide a propósito): quitarla exige apagar el
 * disparador un momento, y eso solo se hace aquí.
 */
public final class TerminosDePrueba {

    private TerminosDePrueba() {
    }

    /** Publica como versión 1 el borrador de cada formación que todavía no tiene versión publicada. */
    public static void publicarEnTodas(JdbcTemplate jdbc) {
        jdbc.update("""
                UPDATE renaser.terminos_versiones SET estado = 'PUBLICADA', numero = 1, publicada_en = now()
                WHERE estado = 'BORRADOR' AND NOT EXISTS (SELECT 1 FROM renaser.terminos_versiones p WHERE p.formacion = terminos_versiones.formacion AND p.estado = 'PUBLICADA')
                """);
    }

    /** Deja los términos como los dejó la V149 (antes de la V162): la versión 1 de la Escuela publicada y un borrador (copia) en las otras dos. Borra las aceptaciones de lo que se quita. */
    public static void restablecer(JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE renaser.terminos_versiones DISABLE TRIGGER terminos_versiones_constancia");
        try {
            jdbc.update("DELETE FROM renaser.aceptaciones_de_terminos WHERE version_id IN (SELECT id FROM renaser.terminos_versiones "
                    + "WHERE NOT (formacion = 'ESCUELA' AND estado = 'PUBLICADA' AND numero = 1))");
            jdbc.update("DELETE FROM renaser.terminos_versiones WHERE NOT (formacion = 'ESCUELA' AND estado = 'PUBLICADA' AND numero = 1)");
            jdbc.update("""
                    INSERT INTO renaser.terminos_versiones (id, formacion, estado, numero, titulo, introduccion, clausulas, texto_casilla)
                    SELECT gen_random_uuid(), f.codigo, 'BORRADOR', NULL, v.titulo, v.introduccion, v.clausulas, v.texto_casilla
                    FROM renaser.formaciones f CROSS JOIN renaser.terminos_versiones v
                    WHERE v.formacion = 'ESCUELA' AND v.numero = 1 AND f.codigo <> 'ESCUELA'
                    """);
        } finally {
            jdbc.execute("ALTER TABLE renaser.terminos_versiones ENABLE TRIGGER terminos_versiones_constancia");
        }
    }
}
