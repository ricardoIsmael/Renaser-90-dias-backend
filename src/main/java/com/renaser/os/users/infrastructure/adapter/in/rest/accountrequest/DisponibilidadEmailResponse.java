package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

/**
 * Respuesta de {@code POST /account-requests/check-email}. {@code available == false} = ya hay
 * cuenta o solicitud con ese correo.
 *
 * <p>Es la MISMA consulta que {@link ExistenciaCuentaResponse}, con el significado invertido:
 * aca el caso feliz es "si, puedes registrarte" y alli es "si, te podemos ayudar a recuperarla".
 * Dos respuestas y dos rutas en vez de una compartida, porque una sola habria dejado el
 * significado del booleano al reves segun quien llamara (razon heredada del repo viejo, AR-04
 * vs AR-06).
 */
public record DisponibilidadEmailResponse(boolean available) {
}
