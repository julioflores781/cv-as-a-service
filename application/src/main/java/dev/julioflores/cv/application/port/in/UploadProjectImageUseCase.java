package dev.julioflores.cv.application.port.in;

import dev.julioflores.cv.application.image.UploadedImage;

public interface UploadProjectImageUseCase {

    /**
     * Valida por sus bytes (JPEG, PNG, WebP o GIF, hasta 5 MB), genera la clave en el servidor y la sube. Ignora el nombre y el
     * tipo que declare el cliente.
     */
    UploadedImage upload(byte[] content);
}
