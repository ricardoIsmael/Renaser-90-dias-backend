package com.renaser.os.calendar.domain.model.meet;

/**
 * Cómo queda la sala de Meet de un evento (spec 040, FR-023). Corregido 2026-11-05 (decisión del dueño: «el Meet se crea solo y los demás
 * entran sin tanto ajetreo»): Renaser SIEMPRE pide {@link #ABIERTA} (se entra directo con el enlace) y ya no se elige en cada evento.
 * {@link #CON_PERMISO} queda solo para describir lo que Google dejó cuando no aceptó abrirla ({@code TRUSTED}, leído de vuelta): así
 * Administración ve «La sala quedó con permiso; no pudimos dejarla abierta.» con «Reintentar».
 */
public enum AccesoSalaMeet {

    ABIERTA,
    CON_PERMISO;

    /** El único acceso que Renaser pide para una sala. */
    public static AccesoSalaMeet porDefecto() {
        return ABIERTA;
    }
}
