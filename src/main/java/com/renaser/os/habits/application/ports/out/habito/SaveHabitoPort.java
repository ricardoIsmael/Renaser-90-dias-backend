package com.renaser.os.habits.application.ports.out.habito;

import com.renaser.os.habits.domain.model.habito.Habito;

public interface SaveHabitoPort {

    Habito save(Habito habito);
}
