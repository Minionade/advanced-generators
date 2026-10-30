package net.bdew.generators.machine;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The machine border is lit like a block face (see MachineBorderRenderer), so its texture must stay the original dark
 * slate. A lighter replacement once hid a lighting bug and left the border the wrong colour in the world.
 */
class EdgeTextureTest {
    @Test void borderTextureIsTheOriginalSlate() throws Exception {
        try (var in = EdgeTextureTest.class.getResourceAsStream("/assets/advgenerators/textures/block/connected/edge.png")) {
            assertNotNull(in);
            var image = ImageIO.read(in);
            int argb = image.getRGB(0, 0);
            assertEquals(255, argb >>> 24, "the frame is opaque");
            assertEquals(72, (argb >> 16) & 0xff);
            assertEquals(81, (argb >> 8) & 0xff);
            assertEquals(84, argb & 0xff);
            assertEquals(0, image.getRGB(16, 16) >>> 24, "the middle is transparent");
        }
    }
}
