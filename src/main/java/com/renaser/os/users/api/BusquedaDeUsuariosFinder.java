package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Quiénes coinciden con lo que alguien escribió en un buscador (D-249): por su nombre o su correo, sin
 * importar tildes ni mayúsculas. Lo pide la lista de soportes del chat, que no puede leer {@code usuarios}
 * por su cuenta (cada módulo pregunta a {@code users} por su {@code api}).
 *
 * <p>Devuelve ids y no resúmenes: quien llama cruza esos ids con lo suyo (sus conversaciones) y recién ahí
 * pagina. Sin tope: con un padrón de cientos o pocos miles de cuentas, una búsqueda de una letra devuelve a
 * lo sumo eso, y cortar acá dejaría huecos en la página de quien llama.
 */
public interface BusquedaDeUsuariosFinder {

    /**
     * @param texto lo escrito; vacío o solo espacios no busca nada (devuelve vacío, no «todos»)
     * @return los ids cuyo nombre o correo contiene el texto, ya normalizado (sin tildes, en minúsculas)
     */
    Set<UserId> coincidenCon(String texto);
}
