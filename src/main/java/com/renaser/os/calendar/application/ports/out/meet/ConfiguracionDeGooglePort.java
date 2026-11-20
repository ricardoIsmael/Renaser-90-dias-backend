package com.renaser.os.calendar.application.ports.out.meet;

/**
 * Lo que el servidor tiene configurado para hablar con Google (spec 040): las credenciales del cliente OAuth y la clave de cifrado del permiso
 * permanente llegan por variables de entorno o SSM, nunca en el código. Sin ellas no se puede conectar la cuenta.
 */
public interface ConfiguracionDeGooglePort {

    /** Hay credenciales de OAuth, dirección de regreso y clave de cifrado. */
    boolean estaConfigurada();

    /** El Gmail que tiene que ser la cuenta conectada (`renasercorporation@gmail.com`); vacío = no se comprueba. Evita conectar por error otra cuenta. */
    String correoEsperado();

    /** Cuántos días hacia adelante se crean las salas de un evento que se repite ({@code EVENTOS_MEET_VENTANA_DIAS}, 60 por defecto). */
    int ventanaEnDias();

    /** Invitar al evento de Calendar a quien lo crea o edita ({@code EVENTOS_MEET_INVITAR_AL_CREADOR}, sí por defecto; FR-035). */
    default boolean invitarAlCreador() {
        return true;
    }

    /** Pedir que la sala se grabe sola ({@code EVENTOS_MEET_GRABAR}, sí por defecto; FR-036). */
    default boolean grabar() {
        return true;
    }

    /** Pedir que la sala se transcriba sola ({@code EVENTOS_MEET_TRANSCRIBIR}, sí por defecto; FR-036). */
    default boolean transcribir() {
        return true;
    }

    /** Pedir las notas automáticas de Gemini («Tomar notas por mí») ({@code EVENTOS_MEET_NOTAS}, sí por defecto; FR-036). */
    default boolean tomarNotas() {
        return true;
    }

    /**
     * Hacer coanfitrión de cada sala a quien dirige la sesión, para que Meet grabe y transcriba solo ({@code EVENTOS_MEET_COANFITRIONES}, NO por
     * defecto; spec 040 FR-038). Apagado, no se llama a Google. Prenderlo exige el permiso {@code meetings.space.created}: ver el README de la spec.
     */
    default boolean coanfitriones() {
        return false;
    }

    /**
     * Crear las salas NUEVAS con la Meet API ({@code spaces.create}) en vez de como conferencia de Calendar ({@code EVENTOS_MEET_SALAS_CON_MEET_API},
     * NO por defecto; spec 040 FR-040). Apagado, todo sigue como siempre. Las salas que ya existen no se tocan salvo que también esté prendido
     * {@link #migrarSalasExistentes()}.
     */
    default boolean salasConMeetApi() {
        return false;
    }

    /**
     * Reemplazar por una sala de la Meet API las ya creadas de hoy en adelante y cerrar la vieja ({@code EVENTOS_MEET_MIGRAR_SALAS}, NO por defecto;
     * spec 040 FR-041). Solo tiene efecto con {@link #salasConMeetApi()} prendido. Apagado (decisión del dueño del 20/11, «solo las nuevas»), las
     * salas existentes no se migran ni se cierran nunca.
     */
    default boolean migrarSalasExistentes() {
        return false;
    }

    /** La migración de FR-041 corre solo si las dos cosas están prendidas: crear con la Meet API y migrar las existentes. */
    default boolean migracionActiva() {
        return salasConMeetApi() && migrarSalasExistentes();
    }
}
