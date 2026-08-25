package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;

public interface SaveRegistroHabitoPort {

    RegistroHabito save(RegistroHabito registro);
}
