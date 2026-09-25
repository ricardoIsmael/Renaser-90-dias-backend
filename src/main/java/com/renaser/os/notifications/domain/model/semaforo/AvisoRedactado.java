package com.renaser.os.notifications.domain.model.semaforo;

/**
 * Título y cuerpo de un aviso del semáforo, con el caso que los eligió. La bandeja y el push llevan
 * el mismo texto ({@code NotificacionService.intentarPush}), así que nunca lleva cifras, colores ni
 * nombres de personas.
 */
public record AvisoRedactado(CasoDelAviso caso, String titulo, String cuerpo) {
}
