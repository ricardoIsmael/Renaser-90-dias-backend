package com.renaser.os.habits.application.ports.out.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;

public interface SaveEntradaDiarioPort {

    EntradaDiario save(EntradaDiario entrada);
}
