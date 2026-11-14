package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Ejemplos de OpenAPI de la gestión de imágenes (claves y dominio ficticios). */
final class AdminImagesExamples {

    private AdminImagesExamples() {}

    static final String UPLOADED = """
            {
              "key": "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp",
              "url": "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp",
              "contentType": "image/webp",
              "bytes": 183422,
              "width": 1280,
              "height": 720
            }""";

    static final String LIST = """
            {
              "images": [
                {
                  "key": "site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp",
                  "url": "https://media.example.com/site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp",
                  "bytes": 183422,
                  "lastModified": "2026-11-10T18:00:00Z",
                  "referenced": true
                },
                {
                  "key": "site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png",
                  "url": "https://media.example.com/site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png",
                  "bytes": 90311,
                  "lastModified": "2026-11-09T09:30:00Z",
                  "referenced": false
                }
              ]
            }""";

    static final String CLEANUP_DRY_RUN = """
            {
              "dryRun": true,
              "deleted": [],
              "wouldDelete": ["site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png"],
              "kept": 4
            }""";

    static final String CLEANUP_DONE = """
            {
              "dryRun": false,
              "deleted": ["site/projects/9f1c2d7e-2222-4a2b-8c3d-bbbbbbbbbbbb.png"],
              "wouldDelete": [],
              "kept": 4
            }""";

    static final String IMAGE_INVALID = """
            {
              "type": "about:blank",
              "title": "Imagen no válida",
              "status": 400,
              "detail": "El archivo falta, está vacío o no es una imagen JPEG, PNG, WebP o GIF legible.",
              "code": "IMAGE_INVALID"
            }""";

    static final String IMAGE_TOO_LARGE = """
            {
              "type": "about:blank",
              "title": "Imagen demasiado grande",
              "status": 413,
              "detail": "La imagen supera el límite de 5 MB.",
              "code": "IMAGE_TOO_LARGE"
            }""";

    static final String IMAGE_UNSUPPORTED = """
            {
              "type": "about:blank",
              "title": "Tipo de imagen no admitido",
              "status": 415,
              "detail": "Solo se admiten imágenes JPEG, PNG, WebP y GIF (no SVG ni otros formatos).",
              "code": "IMAGE_UNSUPPORTED_TYPE"
            }""";

    static final String IMAGE_STORAGE_UNAVAILABLE = """
            {
              "type": "about:blank",
              "title": "Almacenamiento de imágenes no disponible",
              "status": 503,
              "detail": "El almacenamiento de imágenes no está configurado o no responde. El resto de la API funciona con normalidad.",
              "code": "IMAGE_STORAGE_UNAVAILABLE"
            }""";

    static final String IMAGE_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Imagen no encontrada",
              "status": 404,
              "detail": "No existe ninguna imagen con el id «9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa».",
              "code": "IMAGE_NOT_FOUND"
            }""";

    static final String IMAGE_IN_USE = """
            {
              "type": "about:blank",
              "title": "Imagen en uso",
              "status": 409,
              "detail": "La imagen «site/projects/9f1c2d7e-1111-4a2b-8c3d-aaaaaaaaaaaa.webp» la usa alguna versión del contenido (activa o histórica) y no se puede borrar.",
              "code": "IMAGE_IN_USE"
            }""";

}
