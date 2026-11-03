package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/** Administración y Alquimista editan y publican los términos de cada formación sin tocar código (spec 027, FR-001 a FR-009). */
public interface GestionarTerminosUseCase {

    /**
     * Guarda el borrador (lo crea si no había). {@code baseNumero} es el número de la versión vigente que quien edita tenía a la vista (vacío si no había
     * ninguna); si ya no es la vigente, otra persona publicó mientras editaba.
     *
     * @throws IllegalStateException    (409) si otra persona publicó mientras se editaba
     * @throws IllegalArgumentException (400) si el texto pasa de sus límites
     */
    VersionDeTerminos guardarBorrador(UserId actor, Formacion formacion, ContenidoDeTerminos contenido, Integer baseNumero);

    /** Tira el borrador sin publicar nada; no falla si no había. */
    void descartarBorrador(UserId actor, Formacion formacion);

    /** Pone el texto de una versión vieja como borrador (no se publica sola). */
    VersionDeTerminos restaurarComoBorrador(UserId actor, Formacion formacion, int numero);

    /**
     * Publica el borrador como la versión nueva. {@code pideNuevaAceptacion}: a quienes ya aceptaron se les pide aceptar de nuevo al abrir la app.
     *
     * @throws IllegalArgumentException (400) si hay cláusulas vacías, sin cláusulas, o la nota pasa de 500 letras
     * @throws IllegalStateException    (409) si no hay borrador, no tiene cambios respecto de la vigente, o otra persona publicó mientras se editaba
     */
    VersionDeTerminos publicar(UserId actor, Formacion formacion, String nota, boolean pideNuevaAceptacion, Integer baseNumero);
}
