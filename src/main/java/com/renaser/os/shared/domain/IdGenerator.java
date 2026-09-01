package com.renaser.os.shared.domain;

import java.util.UUID;

/**
 * Puerto de generacion de identidad, hermano de {@link Clock} y con el mismo estatus.
 *
 * <p><b>Por que existe:</b> CLAUDE.MD §5.4.7 pide que {@code domain/} sea puro — "sin I/O, sin
 * reloj del sistema, sin aleatoriedad". El reloj ya estaba resuelto asi; la identidad no:
 * habia 33 {@code UUID.randomUUID()} dentro de {@code domain/}. Lo que rompe no es que un UUID
 * sea "azar de negocio" (no lo es, es identidad) sino que <b>no es referencialmente
 * transparente</b>: una factoria que llama a {@code randomUUID()} devuelve un objeto distinto en
 * cada invocacion, y eso hace imposible comparar el agregado esperado contra el obtenido. Ya
 * mordio una vez, con {@code Evento.crear(...)} (ver {@code docs/MODULO_CALENDAR.md}).
 *
 * <p>Hay una segunda razon, que no depende de los tests: una entidad no puede verificar unicidad
 * mas alla de su propio borde, asi que asignar identidad es conceptualmente una operacion
 * <b>externa</b> al agregado. Es la postura de Vaughn Vernon ({@code ProductRepository.nextIdentity()},
 * generado en el adaptador de persistencia) y la de {@code citerus/dddsample-core}
 * ({@code CargoRepository.nextTrackingId()}, declarado en el dominio y resuelto en infraestructura).
 *
 * <p><b>Como se usa:</b> el caso de uso pide el id y se lo pasa a la factoria del agregado —
 * {@code AccountRequest.submit(idGenerator.newId(), ...)}, nunca {@code AccountRequest.submit(...)}
 * generandolo por dentro. Los value objects de identidad ({@code AccountRequestId}, {@code EventoId}…)
 * se quedan en {@code domain/} con su factoria pura {@code of(UUID)}; lo que no puede vivir ahi es la
 * generacion.
 *
 * <p><b>En tests</b> se sustituye por una implementacion determinista (secuencial o fija), igual que
 * se hace hoy con {@link Clock}.
 */
public interface IdGenerator {

    UUID newId();
}
