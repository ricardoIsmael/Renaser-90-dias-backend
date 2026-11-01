package com.renaser.os.community.application.ports.out.testimonio;

import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.shared.domain.AlcanceDeFormacion;

import java.util.List;

public interface LoadTestimonioPort {

    /** Destacados, mas nuevo primero (testimonios/repository.ts:8-22), de las formaciones del alcance (spec 024). */
    List<Testimonio> listarDestacados(int limite, AlcanceDeFormacion alcance);
}
