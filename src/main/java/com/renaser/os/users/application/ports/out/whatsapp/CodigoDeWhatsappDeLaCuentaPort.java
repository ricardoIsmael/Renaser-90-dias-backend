package com.renaser.os.users.application.ports.out.whatsapp;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;

/**
 * Codigo de 6 digitos que prueba que la persona controla un numero de WhatsApp (spec 051). Vive solo en Redis, como el del alta y el de la
 * recuperacion, y es de espacio de claves propio: uno pedido para otra cosa no sirve aca. Va keyed por (cuenta, numero): el codigo de un numero
 * no confirma otro, y pedir uno nuevo para el mismo numero invalida el anterior y reinicia sus intentos.
 */
public interface CodigoDeWhatsappDeLaCuentaPort {

    /** Codigo nuevo para esa cuenta y ese numero ({@code +51987654321}); reemplaza cualquier anterior. */
    String generarCodigo(UserId cuenta, String numero, Duration vigencia);

    /** Compara y, si coincide, lo consume (un solo uso). Al llegar a {@code maxIntentos} fallidos, borra el codigo entero. */
    boolean verificarCodigo(UserId cuenta, String numero, String codigo, int maxIntentos);
}
