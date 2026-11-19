package com.renaser.os.leadership.application.services;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * De dónde salen los datos de cada documento, ya autorizados (spec 056, FR-041): quien la implementa decide quién puede pedir qué y reúne
 * lo que el documento dice. {@link DocumentoSemanalService} solo sabe qué hacer con ellos —sacar la huella, reutilizar o generar, guardar y
 * firmar—, y por eso se prueba sin ninguna de las lecturas de la ficha.
 */
interface FuenteDeDocumentos {

    /** @param semanaHasta el viernes que cierra la semana; null = la semana en curso de la persona */
    DocumentoArmado delAprendiz(UserId actorId, UUID personaId, LocalDate semanaHasta);

    DocumentoArmado delMentor(UserId actorId, UUID mentorId, LocalDate semanaHasta);

    /** @param mes {@code AAAA-MM} */
    DocumentoArmado delMes(UserId actorId, String mes);
}
