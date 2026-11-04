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
}
