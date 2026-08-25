package com.renaser.os.community.application.ports.out.testimonio;

import com.renaser.os.community.domain.model.testimonio.Testimonio;

public interface SaveTestimonioPort {

    Testimonio save(Testimonio testimonio);
}
