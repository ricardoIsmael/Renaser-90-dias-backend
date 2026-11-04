package com.renaser.os.calendar.application.ports.out.meet;

/**
 * Cifra y descifra los secretos que Renaser guarda de la cuenta de Google —el permiso permanente y el verificador de PKCE— (spec 040, FR-011). La
 * clave vive fuera de la base y del repositorio; el texto cifrado lleva el identificador de la clave con que se cifró, para poder rotarla sin
 * perder lo ya guardado.
 */
public interface CifradoDeSecretosPort {

    /** Hay clave de cifrado configurada. Sin ella no se puede guardar nada. */
    boolean estaConfigurado();

    String cifrar(String textoPlano);

    String descifrar(String textoCifrado);

    /** El identificador de la clave que hoy cifra (se guarda junto al dato). */
    int versionDeLaClave();
}
