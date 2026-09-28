package com.renaser.os.onboarding.api;

/**
 * Qué pasó con la Caja Renaser de un aprendiz y a quién hay que avisarle (D-219, spec §5). Lo escuchan
 * {@code notifications} (la bandeja y el push) y {@code chat} (un mensaje del programa en su soporte).
 */
public enum AvisoDeCaja {

    /** Pasó sola a «en revisión» (cumplió el requisito): al aprendiz y a los Admin. */
    EN_REVISION(true, true),
    /** El Admin la aprobó caso por caso: solo al aprendiz (el Admin ya lo sabe). */
    APROBADA(true, false),
    /** El Admin empezó a armarla (o a rearmarla, en un reenvío). */
    ARMANDO(true, false),
    /** Salió: con el medio, el código y la foto de la caja armada. */
    EN_CAMINO(true, false),
    /** A los 3 días de salir sin confirmar: «¿Ya te llegó?». */
    RECORDATORIO(true, false),
    /** A los 5 días de salir sin confirmar: al Admin. */
    SIN_CONFIRMAR(false, true),
    /** Llegó (lo confirmó el aprendiz o lo marcó el Admin): la invitación a compartirla en el Muro. */
    ENTREGADA(true, false);

    private final boolean alAprendiz;
    private final boolean alAdmin;

    AvisoDeCaja(boolean alAprendiz, boolean alAdmin) {
        this.alAprendiz = alAprendiz;
        this.alAdmin = alAdmin;
    }

    public boolean alAprendiz() {
        return alAprendiz;
    }

    public boolean alAdmin() {
        return alAdmin;
    }

    /**
     * La parte de la clave de deduplicación: aprobada y en revisión le dicen al aprendiz lo mismo, así que
     * comparten clave y no le llega dos veces.
     */
    public String claveDeduplicacion() {
        return this == APROBADA ? EN_REVISION.name() : name();
    }
}
