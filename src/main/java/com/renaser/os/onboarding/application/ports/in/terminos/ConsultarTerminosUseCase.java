package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que Administración y Alquimista leen de los términos: el resumen por formación, el detalle con su historial, quiénes aceptaron y la constancia de una persona. */
public interface ConsultarTerminosUseCase {

    /** Las tres formaciones, en su orden. */
    List<ResumenDeTerminos> resumen(UserId actor);

    DetalleDeTerminos detalle(UserId actor, Formacion formacion);

    /** @throws java.util.NoSuchElementException (404) si esa versión no existe */
    VersionDeTerminos version(UserId actor, Formacion formacion, int numero);

    /** Quiénes aceptaron esa versión (o una posterior) y quiénes de la formación todavía no. */
    QuienesAceptaron quienes(UserId actor, Formacion formacion, int numero);

    /** Qué versión aceptó una persona, cuándo y con qué firma (FR-019). */
    List<ConstanciaDeUnaPersona> constanciaDe(UserId actor, UserId persona);

    /** @param vigente vacío = «Por revisar»: la formación no tiene ninguna versión publicada y no puede aceptar personas */
    record ResumenDeTerminos(Formacion formacion, String nombre, Optional<VersionDeTerminos> vigente, boolean hayBorrador, long aceptantes) {
    }

    record DetalleDeTerminos(Formacion formacion, String nombre, Optional<VersionDeTerminos> vigente, Optional<VersionDeTerminos> borrador,
                             List<VersionDelHistorial> versiones) {
    }

    record VersionDelHistorial(VersionDeTerminos version, long aceptantes, String publicadaPor) {
    }

    record QuienesAceptaron(int numero, List<Persona> aceptaron, List<Persona> faltan) {
    }

    record Persona(UserId id, String nombre) {
    }

    /** @param firmaUrl enlace de lectura de la firma, que vence a los 15 minutos; {@code null} si el archivo ya no está */
    record ConstanciaDeUnaPersona(Formacion formacion, int numero, Instant aceptadaEn, String firmaUrl) {
    }
}
