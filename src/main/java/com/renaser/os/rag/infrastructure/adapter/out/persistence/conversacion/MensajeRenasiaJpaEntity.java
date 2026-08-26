package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code usuarioId} referencia `conversaciones_renasia.usuario_id` (la conversacion), no
 * `usuarios` directamente — docs/MODULO_RAG.md §2.
 *
 * <p><b>D-49:</b> {@code marcadoPorUsuario}, {@code notaMarca} y {@code anuladoPorAdmin} se
 * mapean SOLO para persistir sus valores por defecto de la BD congelada
 * ({@code false}/{@code null}) — no hay caso de uso ni endpoint que los use, ni el dominio
 * los expone. Es deliberado: el dueno del proyecto quito la funcion de "marcar" un mensaje.
 */
@Entity
@Table(name = "mensajes_renasia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeRenasiaJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RolMensajeRenasiaJpa rol;

    private String contenido;

    /** D-49: siempre {@code false} al escribir — sin caso de uso que lo mute. */
    private boolean marcadoPorUsuario;

    /** D-49: siempre {@code null} al escribir — sin caso de uso que lo mute. */
    private String notaMarca;

    /** D-49: siempre {@code false} al escribir — sin caso de uso que lo mute. */
    private boolean anuladoPorAdmin;

    private Instant creadoEn;
}
