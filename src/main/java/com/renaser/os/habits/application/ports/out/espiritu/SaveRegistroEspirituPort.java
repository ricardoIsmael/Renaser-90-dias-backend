package com.renaser.os.habits.application.ports.out.espiritu;

import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;

public interface SaveRegistroEspirituPort {

    RegistroEspiritu save(RegistroEspiritu registro);
}
