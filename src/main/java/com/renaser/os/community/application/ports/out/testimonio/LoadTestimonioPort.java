package com.renaser.os.community.application.ports.out.testimonio;

import com.renaser.os.community.domain.model.testimonio.Testimonio;

import java.util.List;

public interface LoadTestimonioPort {

    /** Destacados, mas nuevo primero (testimonios/repository.ts:8-22). */
    List<Testimonio> listarDestacados(int limite);
}
