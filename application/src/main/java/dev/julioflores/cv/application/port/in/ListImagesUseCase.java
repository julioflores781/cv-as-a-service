package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.image.ImageEntry;
import java.util.List;

public interface ListImagesUseCase {

    /** Las imágenes de proyectos del almacenamiento, de la más nueva a la más antigua. */
    List<ImageEntry> list();
}
