package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;

/**
 * ¿Esa formación ya tiene términos y condiciones publicados? <b>Lo implementa {@code onboarding}</b> (spec 027, dueño de los términos) y lo consume
 * {@code users} al aceptar una solicitud: una formación sin términos publicados no recibe personas nuevas (FR-021). La dependencia va de {@code onboarding}
 * hacia {@code users}, como siempre (el mismo patrón de {@link FichaAlAceptar}).
 */
public interface TerminosPublicados {

    boolean estanPublicados(Formacion formacion);
}
