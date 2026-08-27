package com.renaser.os.onboarding.application.ports.in.metamaestra;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Filtro de las 6 Ps sobre la Meta Maestra escrita (Diseno de Destino), unico paso de
 * onboarding que evalua un TEXTO libre en vez de una grabacion. Contrato SINCRONO,
 * deliberadamente distinto del contrato async+polling de {@code ValidarV90UseCase} — ver
 * javadoc de {@code ValidacionMetaMaestraPort} para el porque completo. Resumen: el texto
 * se valida ANTES de persistirse como respuesta (el aprendiz puede reintentar en el mismo
 * borrador tantas veces como quiera mientras escribe), asi que no hay una fila propia
 * donde colgar un estado {@code PROCESANDO} contra el que hacer polling sin violar D-40
 * (BD congelada, sin tablas nuevas).
 *
 * <p>Limite de 3000 caracteres y no-vacio: mismo contrato que el backend viejo
 * ({@code ValidateSmartTextInput} en RenaserBack/src/features/onboarding/schema.ts).
 */
public interface ValidarMetaMaestraUseCase {

    ResultadoMetaMaestra validar(ValidarMetaMaestraCommand command);

    record ValidarMetaMaestraCommand(@NotNull UserId actorId, @NotBlank @Size(max = 3000) String texto) {

        public ValidarMetaMaestraCommand {
            SelfValidating.validateConstructorArgs(ValidarMetaMaestraCommand.class, actorId, texto);
        }
    }

    record ResultadoMetaMaestra(Veredicto veredicto, List<String> pesFaltantes, String feedback) {

        public enum Veredicto {
            APROBADA,
            RECHAZADA,
            /**
             * Falla tecnica del proveedor de IA (o, en este alcance, el placeholder NoOp):
             * NUNCA bloquea al aprendiz — mismo criterio "fail-open" documentado y probado en
             * el backend viejo ({@code validateSmartText.test.ts}: "never blocks the trainee
             * on a technical AI failure — accepts with pendingReview").
             */
            PENDIENTE_DE_REVISION
        }
    }
}
