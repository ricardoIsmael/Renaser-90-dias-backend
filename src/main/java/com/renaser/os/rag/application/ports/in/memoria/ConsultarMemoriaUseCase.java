package com.renaser.os.rag.application.ports.in.memoria;

import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Optional;

/** Lo que el acompanante recuerda de una persona (D-167). */
public interface ConsultarMemoriaUseCase {

    /** Lo guardado, para que la persona lo vea en su perfil: aunque la memoria este apagada. */
    MemoriaEnElPerfil paraElPerfil(UserId actorId);

    /**
     * Lo que entra al prompt del acompanante. Vacio si la memoria esta apagada
     * ({@code renaser.ia.acompanante.memoria}) o no se pudo leer: entonces el prompt queda como antes
     * de D-167, sin la seccion. Presente aunque todavia no sepa nada: el prompt lo dice.
     */
    Optional<MemoriaDeRenasia> paraConversar(UserId actorId);

    /**
     * @param activa si la memoria esta encendida. Apagada, la app oculta la seccion salvo que haya
     *               algo guardado de antes: eso se sigue viendo, para poder borrarlo
     */
    record MemoriaEnElPerfil(MemoriaDeRenasia memoria, boolean activa) {

        public MemoriaEnElPerfil {
            Objects.requireNonNull(memoria, "memoria es obligatoria");
        }
    }
}
