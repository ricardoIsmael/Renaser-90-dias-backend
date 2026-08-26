/**
 * Contrato publico de `rag` hacia otros modulos. Hoy vacio a proposito: ningun modulo
 * consume nada de `rag` — es el consumidor final de la cadena (lee de `habits` via
 * {@code habits.api.EntradaDiarioFinder} y de `users` via sus finders, pero nadie lee de el).
 *
 * <p>El paquete existe igual para que el modulo tenga su {@code @NamedInterface} declarada
 * desde el dia uno: si manana algo necesita, por ejemplo, saber si un aprendiz ya tiene su
 * informe semanal, el lugar ya esta y no hay que decidirlo con apuro.
 */
@org.springframework.modulith.NamedInterface("api")
package com.renaser.os.rag.api;
