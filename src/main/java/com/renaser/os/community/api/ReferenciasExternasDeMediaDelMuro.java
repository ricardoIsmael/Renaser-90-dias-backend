package com.renaser.os.community.api;

import java.util.Collection;
import java.util.Set;

/**
 * Lo que OTRO modulo declara sobre las claves {@code muro/} que todavia necesita.
 *
 * <p><b>Para que existe.</b> El bucket es uno solo y una clave {@code muro/} puede tener mas de
 * un dueno: compartir una publicacion en el chat <b>no copia el archivo</b>, persiste la MISMA
 * clave en el mensaje ({@code CompartirPublicacionService}; {@code PublicacionParaCompartir}:
 * "vuelve a firmar en cada lectura" y "no se copia un solo byte"). Cuando el Muro retira del
 * bucket el objeto de una publicacion que se borra, tiene que saber primero si alguien mas lo
 * sigue mirando — si no, deja esa foto en 404 para siempre dentro de una conversacion privada que
 * ninguna moderacion toco.
 *
 * <p><b>Por que un contrato que implementan los demas, y no una consulta de community.</b> Dos
 * razones, y las dos son reglas escritas de este repositorio:
 * <ul>
 *   <li>{@code .claude/rules/01-arquitectura-hexagonal}: <i>"Ningun modulo manda SQL nativo contra
 *       una tabla ajena"</i>. {@code mensajes} es de {@code chat}; community no puede leerla.</li>
 *   <li>La dependencia no puede invertirse: {@code chat} ya importa {@code community.api}
 *       ({@code CompartirPublicacionService}), asi que un {@code community -> chat.api} cerraria
 *       un ciclo y {@code ArchitectureTest.modulesDoNotLeakInternals} rompe el build.</li>
 * </ul>
 * Por eso cada modulo que referencie claves del Muro publica un bean de esta interfaz y responde
 * por su propia tabla. La direccion de la dependencia queda como estaba.
 *
 * <p><b>Se inyecta como {@code List<ReferenciasExternasDeMediaDelMuro>} y eso es a proposito.</b>
 * Si manana un modulo empieza a referenciar claves del Muro, agrega su implementacion y el borrado
 * la consulta sin que haya que tocar community. Y si el unico implementador de hoy desaparece, el
 * contexto de Spring no arranca: preferible a que el Muro empiece a borrar en silencio objetos que
 * el chat todavia sirve.
 *
 * <p>Las respuestas son de LECTURA. Quien decide que se borra es
 * {@code PublicacionMuroService}, donde la regla se puede probar sin base de datos.
 */
public interface ReferenciasExternasDeMediaDelMuro {

    /**
     * De las claves dadas, las que este modulo todavia referencia.
     *
     * <p>Fail-closed por contrato: ante la duda se devuelve la clave (se retiene el objeto), nunca
     * se omite. Un tombstone NO libera la clave — en el chat borrar un mensaje es
     * {@code eliminado_en} y la fila sigue ahi, con {@code urlDeLectura} firmandola en cada
     * lectura.
     *
     * @param rutasMuro claves {@code muro/...}, nunca vacia
     * @return subconjunto de {@code rutasMuro}; vacio si este modulo no necesita ninguna
     */
    Set<String> referenciadas(Collection<String> rutasMuro);
}
