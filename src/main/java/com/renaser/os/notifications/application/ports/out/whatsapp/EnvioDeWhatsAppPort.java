package com.renaser.os.notifications.application.ports.out.whatsapp;

import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;

import java.util.List;
import java.util.UUID;

/**
 * El envio por WhatsApp, detras de un puerto (spec 041, FR-018): el primer adaptador habla con YCloud; si
 * un dia se cambia a la API de Meta directa, el resto del sistema no se entera. Las pruebas usan un adaptador falso y nunca llaman a Meta ni a YCloud.
 *
 * <p><b>Sin la configuracion del proveedor este canal no hace nada</b> ({@link #configurado()} es falso) y el chat sigue funcionando (FR-028).
 */
public interface EnvioDeWhatsAppPort {

    /** Si hay clave, numero de emision y plantilla: sin eso no se llama a nadie. */
    boolean configurado();

    /**
     * Manda la plantilla del recordatorio. <b>Nunca lanza</b>: un corte, un tiempo agotado o un 5xx son {@code NO_SE_SUPO} (pudo salir), y el resto de las
     * fallas vuelven como {@code FALLIDO} con su causa. No se reintenta nada.
     *
     * @param destino       el numero en formato internacional ({@code +51987654321})
     * @param externalId    nuestro id de la entrega: el proveedor lo devuelve en sus avisos de estado (no es una llave de idempotencia)
     * @param huecos        los cinco huecos del cuerpo de la plantilla, ya limpios
     * @param cargaDelBoton la carga del boton «No recibir más» ({@code BAJA:<id>})
     */
    ResultadoDelEnvio enviarRecordatorio(String destino, UUID externalId, List<String> huecos, String cargaDelBoton);

    /** Un texto libre, solo valido dentro de las 24 horas que abre un mensaje de la persona (la confirmacion de la baja). Mejor esfuerzo: nunca lanza. */
    void enviarTexto(String destino, String texto);
}
