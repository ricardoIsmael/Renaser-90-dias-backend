package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.plataforma.domain.model.catalogo.PoliticaDeProcesos;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Una funcionalidad como la ve la consola (contrato §6, {@code GET /soporte-ti/funcionalidades}): su definición del
 * catálogo y su estado guardado. {@code prendidaGlobal} y {@code porRol} son los de ESTE nivel, sin la madre: la
 * consola muestra cada interruptor tal como está.
 *
 * @param version       la de la fila, o 0 si nadie la cambió nunca: es la que hay que mandar en el {@code PUT}
 * @param actualizadoPor quién la cambió por última vez, o {@code null} si nunca se cambió
 * @param circuito      solo en las partes de la IA con circuito automático ({@code ia.chat}, {@code ia.voz})
 */
public record FuncionalidadEnConsola(String clave, String madre, String area, String nombre, String descripcion,
                                     Set<UserRole> rolesQueLaUsan, boolean critica, ValorPorDefecto porDefecto,
                                     PoliticaDeProcesos procesos, boolean prendidaGlobal,
                                     Map<UserRole, Boolean> porRol, String aviso, long version,
                                     Actor actualizadoPor, Instant actualizadoEn, CircuitoAutomatico circuito) {

    /** @param nombre {@code null} si la cuenta ya no existe */
    public record Actor(UserId id, String nombre) {
    }
}
