package com.renaser.os.shared.domain;

/**
 * Spec 048 (FR-019, D13): por qué un envío hecho sin red NO se aplicó al llegar. Es un conjunto cerrado —la app lo reconoce por
 * su nombre y la base lo guarda con un CHECK (V190)—, con un mensaje listo para mostrar, en tuteo y con tildes.
 */
public enum CodigoNoAplicado {

    /** Algo de lo que tocabas cambió en el servidor después de la hora en que lo hiciste: gana lo más nuevo. */
    CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR("Esto cambió en tu cuenta después de que lo hiciste sin conexión. Se quedó lo más nuevo."),
    /** El alta ya existía (semana ya planificada, hora del Código Renaser ya ocupada, día en curso ya armado). */
    YA_ESTABA_HECHO("Esto ya estaba hecho en tu cuenta. Se quedó lo que ya había."),
    /** Lo que tocabas ya no está (acción reemplazada, hábito quitado del plan, semana borrada). */
    RECURSO_YA_NO_EXISTE("Lo que tocaste ya no existe en tu cuenta. Se quedó lo del servidor."),
    /** El día al que apunta ya terminó o no es el de la hora real. */
    FUERA_DE_SU_DIA("Esto era de un día que ya terminó y no se pudo aplicar."),
    /** Código Renaser desde el día 8 sin Mi Radar encendido, sin horas ese día o fuera de 03:00–23:59 a la hora real. */
    MI_RADAR_NO_APLICA("Mi Radar no tenía esa hora para ti, así que no se guardó ese Código Renaser.");

    private final String mensaje;

    CodigoNoAplicado(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
