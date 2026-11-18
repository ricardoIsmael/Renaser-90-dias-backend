package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Generar, guardar y entregar el documento de una semana (spec 056, FR-035, FR-036, FR-032, FR-039 y FR-041): el de un aprendiz, el de un
 * mentor o el reporte del mes. Si ya existe el documento de esa persona y esa semana con los mismos datos, se reutiliza y solo se firma un
 * enlace nuevo, que vence a los 10 minutos. Cada pedido queda registrado (FR-040).
 *
 * <p>Quien puede pedir cada uno: el de un aprendiz, el líder de su formación, Administración y Alquimista, y el mentor que lo acompaña hoy
 * ({@code NO_ACOMPANA}); el de un mentor y el del mes, el liderazgo. De otra formación es un 404, como el resto de lecturas del líder
 * (spec 022, FR-020).
 */
public interface GenerarDocumentoSemanalUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    si quien pide no puede: {@code NO_ACOMPANA} para un mentor que no lo acompaña
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA} (400); {@code SIN_PROGRAMA} (404)
     * @throws java.util.NoSuchElementException                       (404) si la persona no existe, no se muestra o es de otra formación
     */
    DocumentoEntregado delAprendiz(PedidoDeDocumento pedido);

    /** Igual, para un mentor: solo el liderazgo; sin su Día 1 el documento trae únicamente lo suyo como mentor. */
    DocumentoEntregado delMentor(PedidoDeDocumento pedido);

    /** El reporte del mes del cuerpo de mentores: solo el liderazgo. */
    DocumentoEntregado delMes(PedidoDeDocumentoDelMes pedido);

    /** @param semanaHasta el viernes que cierra la semana; null = la semana en curso de la persona */
    record PedidoDeDocumento(UserId actorId, UUID personaId, LocalDate semanaHasta) {

        public PedidoDeDocumento {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    /** @param mes {@code AAAA-MM} */
    record PedidoDeDocumentoDelMes(UserId actorId, String mes) {

        public PedidoDeDocumentoDelMes {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(mes, "mes es obligatorio");
        }
    }

    /**
     * @param url         el enlace firmado de lectura; vence en {@code venceEn}
     * @param reutilizado true si el documento ya estaba generado con esos mismos datos
     * @param desde       el primer día del período del documento
     * @param hasta       el último día del período del documento
     */
    record DocumentoEntregado(URI url, Instant venceEn, boolean reutilizado, LocalDate desde, LocalDate hasta) {
    }
}
