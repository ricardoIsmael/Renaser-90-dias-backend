package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Las rutas de objeto que una cuenta deja atras, y cuales de ellas sigue mirando alguien mas.
 *
 * <p><b>Por que son dos metodos y no uno.</b> La decision de que se borra es politica de negocio
 * y vive en {@code AccountDeletionService} —donde se puede probar sin base de datos—; este
 * puerto solo aporta los dos hechos que esa decision necesita y que si o si salen de Postgres.
 * Un unico metodo {@code rutasBorrables()} escondería la regla dentro de un SQL que ninguna
 * prueba unitaria puede ejercitar, que es justo lo que no queremos en un camino que borra.
 *
 * <p><b>Por que hace falta el segundo.</b> En este sistema una misma clave de S3 puede tener mas
 * de un dueno, y las filas que la referencian no siempre caen con la cascada:
 * <ul>
 *   <li>{@code testimonios.usuario_id} es {@code ON DELETE SET NULL} (baseline, linea 1129): la
 *       fila SOBREVIVE a la purga con {@code avatar_url} y {@code foto_evento_ruta} congelados, y
 *       {@code TestimonioService.aVista} vuelve a firmar esa foto en cada listado.</li>
 *   <li>Compartir una publicacion al chat referencia la MISMA clave {@code muro/...}; si el que
 *       compartio es otra persona, su mensaje no cae con la cascada de {@code emisor_id} y se
 *       queda apuntando al objeto.</li>
 * </ul>
 * Borrar el objeto en cualquiera de esos dos casos deja la foto en 404 dentro del producto para
 * alguien que no pidio ninguna baja.
 */
public interface RutasDeAlmacenamientoDeCuentaPort {

    /**
     * Todas las rutas de objeto que guardan las filas de esta cuenta — las que la cascada de
     * Postgres esta por destruir. Sin filtrar y sin deduplicar contra nada: es la foto cruda de
     * lo que hay, y quien decide es el llamador.
     *
     * <p>Tiene que invocarse ANTES del DELETE: despues, las filas que guardaban las rutas ya no
     * existen y no hay forma de saber que objetos quedaron sueltos.
     */
    List<String> candidatas(UserId usuarioId);

    /**
     * De las rutas dadas, las que ademas referencia alguna fila que SOBREVIVE a la purga de esta
     * cuenta. Son las que no se pueden borrar sin romperle la vista a un tercero.
     *
     * @return subconjunto de {@code rutas}; vacio si ninguna esta compartida
     */
    Set<String> referenciadasPorTerceros(UserId usuarioId, Collection<String> rutas);
}
