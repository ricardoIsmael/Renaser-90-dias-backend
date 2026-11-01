package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;

import java.util.Set;

/**
 * Qué tipos de comunidad ofrece cada formación (spec 024, FR-033): los cuatro de la Tribu —general, grupo con mentor, soporte y directos— que
 * Administración prende o apaga por formación. Lo lee {@code chat} para esconder lo apagado y para no crear conversaciones de ese tipo.
 *
 * <p>Vive en {@code users} porque el dato es una columna del catálogo de formaciones ({@code formaciones.tipos_de_comunidad}, V138), que es de este módulo.
 */
public interface ComunidadDeLaFormacionFinder {

    /** Los tipos PRENDIDOS de {@code formacion}. */
    Set<TipoDeComunidad> prendidosDe(Formacion formacion);
}
