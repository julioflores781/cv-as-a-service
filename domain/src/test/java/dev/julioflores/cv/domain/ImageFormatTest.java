package dev.julioflores.cv.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test // T1
    void realHeadersOfEachFormatAreDetectedWithTheirExtensionAndType() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.detect(ImageSamples.jpeg(10, 10, 64)));
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.detect(ImageSamples.png(10, 10, 64)));
        assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.detect(ImageSamples.webp(10, 10, 64)));
        assertEquals(Optional.of(ImageFormat.GIF), ImageFormat.detect(ImageSamples.gif(10, 10, 64)));
        assertEquals("jpg", ImageFormat.JPEG.extension());
        assertEquals("image/jpeg", ImageFormat.JPEG.mediaType());
        assertEquals("png", ImageFormat.PNG.extension());
        assertEquals("image/png", ImageFormat.PNG.mediaType());
        assertEquals("webp", ImageFormat.WEBP.extension());
        assertEquals("image/webp", ImageFormat.WEBP.mediaType());
        assertEquals("gif", ImageFormat.GIF.extension());
        assertEquals("image/gif", ImageFormat.GIF.mediaType());
        byte[] gif87 = ImageSamples.gif(1, 1, 32);
        gif87[4] = '7';
        assertEquals(Optional.of(ImageFormat.GIF), ImageFormat.detect(gif87));
    }

    @Test // T2
    void svgExecutablesEmptyOrTooShortContentHaveNoSupportedFormat() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        byte[] exe = new byte[64];
        exe[0] = 'M';
        exe[1] = 'Z';
        byte[] html = "<html><body>no soy una imagen</body></html>".getBytes(StandardCharsets.UTF_8);
        for (byte[] content : new byte[][] {svg, exe, html, new byte[0], null, new byte[11], {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}}) {
            assertTrue(ImageFormat.detect(content).isEmpty());
        }
        // RIFF que no es WebP (un WAV) y GIF con versión inexistente.
        byte[] wav = ImageSamples.webp(2, 2, 64);
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        assertTrue(ImageFormat.detect(wav).isEmpty());
        byte[] gif = ImageSamples.gif(1, 1, 32);
        gif[4] = '5';
        assertTrue(ImageFormat.detect(gif).isEmpty());
    }

    @Test // T3
    void thePngSignatureWinsWhateverTheClientDeclaredItWas() {
        // La detección solo recibe los bytes: no hay forma de que un nombre «.jpg» o un tipo «image/jpeg» influyan.
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.detect(ImageSamples.png(5, 5, 64)));
    }

    // ── extras fuera del plan ──────────────────────────────────────────────────────

    @Test
    void knownButUnsupportedImageTypesAreToldApartFromGarbage() {
        assertTrue(ImageFormat.isKnownUnsupported("<?xml version=\"1.0\"?><SVG></SVG>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ImageFormat.isKnownUnsupported("  <svg width='1'/>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ImageFormat.isKnownUnsupported(new byte[] {'B', 'M', 0, 0, 0, 0}));
        assertTrue(ImageFormat.isKnownUnsupported(new byte[] {'I', 'I', 0x2A, 0, 0, 0}));
        assertTrue(ImageFormat.isKnownUnsupported(new byte[] {'M', 'M', 0, 0x2A, 0, 0}));
        assertTrue(ImageFormat.isKnownUnsupported(new byte[] {0, 0, 1, 0, 1, 0}));
        assertTrue(ImageFormat.isKnownUnsupported(
                new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0}));
        assertTrue(ImageFormat.isKnownUnsupported(
                new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f', 0, 0}));
        assertFalse(ImageFormat.isKnownUnsupported(
                new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0}));
        assertFalse(ImageFormat.isKnownUnsupported("hola mundo, esto es texto".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ImageFormat.isKnownUnsupported(new byte[2]));
        assertFalse(ImageFormat.isKnownUnsupported(null));
    }

    @Test
    void dimensionsAreReadFromTheHeaderOfEachFormat() {
        assertEquals(new ImageDimensions(1280, 720), dims(ImageFormat.PNG, ImageSamples.png(1280, 720, 64)));
        assertEquals(new ImageDimensions(640, 480), dims(ImageFormat.GIF, ImageSamples.gif(640, 480, 64)));
        assertEquals(new ImageDimensions(1920, 1080), dims(ImageFormat.JPEG, ImageSamples.jpeg(1920, 1080, 64)));
        assertEquals(new ImageDimensions(1200, 630), dims(ImageFormat.WEBP, ImageSamples.webp(1200, 630, 64)));
    }

    @Test
    void lossyAndLosslessWebpDimensionsAreRead() {
        byte[] lossy = new byte[40];
        System.arraycopy(ImageSamples.webp(1, 1, 40), 0, lossy, 0, 40);
        System.arraycopy("VP8 ".getBytes(StandardCharsets.US_ASCII), 0, lossy, 12, 4);
        lossy[26] = (byte) (800 & 0xFF);
        lossy[27] = (byte) (800 >> 8);
        lossy[28] = (byte) (600 & 0xFF);
        lossy[29] = (byte) (600 >> 8);
        assertEquals(new ImageDimensions(800, 600), dims(ImageFormat.WEBP, lossy));

        byte[] lossless = new byte[40];
        System.arraycopy(ImageSamples.webp(1, 1, 40), 0, lossless, 0, 40);
        System.arraycopy("VP8L".getBytes(StandardCharsets.US_ASCII), 0, lossless, 12, 4);
        long bits = (300 - 1) | ((200L - 1) << 14);
        for (int i = 0; i < 4; i++) {
            lossless[21 + i] = (byte) (bits >> (8 * i));
        }
        assertEquals(new ImageDimensions(300, 200), dims(ImageFormat.WEBP, lossless));
    }

    @Test
    void truncatedOrIncoherentHeadersHaveNoDimensions() {
        assertTrue(ImageDimensions.read(ImageFormat.PNG, java.util.Arrays.copyOf(ImageSamples.png(1, 1, 64), 20)).isEmpty());
        assertTrue(ImageDimensions.read(ImageFormat.GIF, new byte[8]).isEmpty());
        assertTrue(ImageDimensions.read(ImageFormat.PNG, ImageSamples.png(0, 5, 64)).isEmpty());
        assertTrue(ImageDimensions.read(ImageFormat.JPEG, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0}).isEmpty());
        byte[] webp = ImageSamples.webp(2, 2, 64);
        webp[12] = 'X';
        assertTrue(ImageDimensions.read(ImageFormat.WEBP, webp).isEmpty());
        assertTrue(ImageDimensions.read(ImageFormat.WEBP, new byte[13]).isEmpty());
    }

    @Test
    void jpegScanSkipsRestartMarkersAndFillBytesAndStopsWithoutAFrame() {
        byte[] withFill = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xFF, (byte) 0xD0, (byte) 0xFF, (byte) 0x01, 0x12,
            (byte) 0xFF, (byte) 0xC2, 0, 11, 8, 0x01, (byte) 0xE0, 0x02, (byte) 0x80, 3, 0, 0};
        assertEquals(new ImageDimensions(640, 480), dims(ImageFormat.JPEG, withFill));
        byte[] noFrame = new byte[40];
        noFrame[0] = (byte) 0xFF;
        noFrame[1] = (byte) 0xD8;
        assertTrue(ImageDimensions.read(ImageFormat.JPEG, noFrame).isEmpty());
        assertThrowsDimensions();
    }

    private static void assertThrowsDimensions() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ImageDimensions(0, 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ImageDimensions(1, -1));
    }

    private static ImageDimensions dims(ImageFormat format, byte[] content) {
        return ImageDimensions.read(format, content).orElseThrow();
    }
}
