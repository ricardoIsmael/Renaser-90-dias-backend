package com.renaser.os.habits.application.ports.in.carrera;

/** Spec 042 (FR-042): la ruta de una carrera se guarda 180 días; después se borra y queda solo su resumen. */
public interface BorrarRutasViejasUseCase {

    /** Borra las rutas vencidas, de a lotes; devuelve cuántas borró. */
    int borrarRutasViejas();
}
