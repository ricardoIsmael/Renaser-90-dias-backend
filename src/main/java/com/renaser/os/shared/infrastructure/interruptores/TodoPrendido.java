package com.renaser.os.shared.infrastructure.interruptores;

import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;

/**
 * {@link Interruptores} sin {@code plataforma}: todo prendido (contrato §1). Solo existe en contextos que no cargan
 * ese módulo, como la prueba de un módulo suelto; en la aplicación completa manda la implementación de
 * {@code plataforma} ({@link InterruptoresPorDefectoConfig}).
 */
public final class TodoPrendido implements Interruptores {

    @Override
    public boolean prendida(String clave) {
        return true;
    }

    @Override
    public boolean prendidaPara(String clave, UserId actor) {
        return true;
    }

    @Override
    public void exigirPrendidaPara(String clave, UserId actor) {
        // Todo prendido: nada que exigir.
    }
}
