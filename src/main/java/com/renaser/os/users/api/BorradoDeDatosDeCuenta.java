package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Lo que cada módulo hace con SUS datos de una persona cuando su cuenta se borra para siempre
 * (D-243): al vencer los 30 días de gracia o cuando un Admin la elimina en el acto.
 *
 * <p><b>Por qué un contrato que implementan los demás.</b> {@code .claude/rules/01}: ningún módulo
 * manda SQL contra una tabla ajena. Hasta D-243 la purga de {@code users} se apoyaba en las FK
 * {@code ON DELETE CASCADE} contra {@code usuarios} y leía con SQL suelto las rutas de archivos de
 * seis módulos ajenos ({@code RutasDeAlmacenamientoDeCuentaJdbcAdapter}, deuda anotada en su propio
 * javadoc). Ahora cada módulo publica un bean de esta interfaz y responde por sus tablas; {@code users}
 * solo orquesta. Las cascadas siguen en la base como red de seguridad, no como el mecanismo.
 *
 * <p>Se inyecta como {@code List<BorradoDeDatosDeCuenta>}, ordenada por {@code @Order}: los módulos
 * cuyas filas cuelgan de filas de otro módulo van antes (por ejemplo {@code evidence} y {@code rocks}
 * antes que {@code habits}, porque {@code evidencias} y {@code eventos_verdugo} referencian
 * {@code registros_habito}).
 *
 * <p>El borrado se hace en tres pasos y en este orden, para no perder la única forma de saber qué
 * archivos había:
 * <ol>
 *   <li>{@link #archivosDe}: las claves de archivo que el módulo guarda en filas que va a borrar.</li>
 *   <li>{@link #archivosEnUsoTrasBorrar}: de todas las claves juntadas, las que el módulo sigue
 *       necesitando en filas que NO se borran (de otras personas). Esas no se borran del bucket.</li>
 *   <li>{@link #borrarDatosDe}: borra (o anonimiza) las filas. Corre dentro de la transacción del
 *       borrado de la cuenta.</li>
 * </ol>
 */
public interface BorradoDeDatosDeCuenta {

    /** Claves de objeto (bucket) guardadas en filas de la persona que este módulo va a borrar. */
    default Set<String> archivosDe(UserId cuenta) {
        return Set.of();
    }

    /**
     * De las claves dadas (las de TODOS los módulos), las que este módulo sigue referenciando en
     * filas que sobreviven al borrado de la cuenta. Ante la duda, se devuelve: no se borra.
     */
    default Set<String> archivosEnUsoTrasBorrar(UserId cuenta, Set<String> claves) {
        return Set.of();
    }

    /** Borra o anonimiza las filas de la persona en las tablas de este módulo. Idempotente. */
    void borrarDatosDe(UserId cuenta);
}
