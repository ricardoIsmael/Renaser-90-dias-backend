package com.renaser.os.calendar.infrastructure.adapter.out.seguridad;

import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * AES-256-GCM con un nonce nuevo por valor (spec 040, FR-011). El texto cifrado es {@code v<versión>:<base64(nonce || cifrado+etiqueta)>}: lleva la
 * versión de la clave con que se cifró, así que se puede rotar la clave sin perder lo guardado (la anterior sigue sirviendo para descifrar).
 *
 * <p>Las claves llegan por la configuración (variables de entorno o SSM), 32 bytes en base64, y nunca se escriben en el código ni en un registro.
 * Sin clave configurada el servidor arranca igual, pero no puede guardar nada de la cuenta de Google.
 */
@Component
class AesGcmCifrado implements CifradoDeSecretosPort {

    private static final int TAMANO_DEL_NONCE = 12;
    private static final int BITS_DE_LA_ETIQUETA = 128;
    private static final int BYTES_DE_LA_CLAVE = 32;

    private final SecureRandom azar = new SecureRandom();
    private final int versionVigente;
    private final Map<Integer, SecretKeySpec> claves = new HashMap<>();

    AesGcmCifrado(@Value("${renaser.calendar.meet.cifrado.clave:}") String clave,
                  @Value("${renaser.calendar.meet.cifrado.version:1}") int version,
                  @Value("${renaser.calendar.meet.cifrado.clave-anterior:}") String claveAnterior,
                  @Value("${renaser.calendar.meet.cifrado.version-anterior:0}") int versionAnterior) {
        this.versionVigente = version;
        cargar(clave, version);
        cargar(claveAnterior, versionAnterior);
    }

    private void cargar(String claveEnBase64, int version) {
        if (claveEnBase64 == null || claveEnBase64.isBlank()) {
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(claveEnBase64.trim());
        if (bytes.length != BYTES_DE_LA_CLAVE) {
            throw new IllegalStateException("La clave de cifrado de la cuenta de Google tiene que ser de 32 bytes (AES-256) en base64.");
        }
        claves.put(version, new SecretKeySpec(bytes, "AES"));
    }

    @Override
    public boolean estaConfigurado() {
        return claves.containsKey(versionVigente);
    }

    @Override
    public int versionDeLaClave() {
        return versionVigente;
    }

    @Override
    public String cifrar(String textoPlano) {
        SecretKeySpec clave = claves.get(versionVigente);
        if (clave == null) {
            throw new IllegalStateException("Falta la clave de cifrado de la cuenta de Google (renaser.calendar.meet.cifrado.clave).");
        }
        try {
            byte[] nonce = new byte[TAMANO_DEL_NONCE];
            azar.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_DE_LA_ETIQUETA, nonce));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            byte[] todo = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, todo, 0, nonce.length);
            System.arraycopy(cifrado, 0, todo, nonce.length, cifrado.length);
            return "v" + versionVigente + ":" + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto de la cuenta de Google.", e);
        }
    }

    @Override
    public String descifrar(String textoCifrado) {
        int separador = textoCifrado.indexOf(':');
        if (separador < 2 || textoCifrado.charAt(0) != 'v') {
            throw new IllegalStateException("El secreto guardado no tiene el formato esperado.");
        }
        SecretKeySpec clave = claves.get(versionDe(textoCifrado, separador));
        if (clave == null) {
            throw new IllegalStateException("Falta la clave de cifrado con que se guardó el secreto de la cuenta de Google.");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(textoCifrado.substring(separador + 1));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_DE_LA_ETIQUETA, todo, 0, TAMANO_DEL_NONCE));
            return new String(cipher.doFinal(todo, TAMANO_DEL_NONCE, todo.length - TAMANO_DEL_NONCE), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto de la cuenta de Google.", e);
        }
    }

    private static int versionDe(String textoCifrado, int separador) {
        try {
            return Integer.parseInt(textoCifrado.substring(1, separador));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("El secreto guardado no tiene el formato esperado.", e);
        }
    }
}
