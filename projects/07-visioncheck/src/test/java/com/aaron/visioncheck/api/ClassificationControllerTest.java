package com.aaron.visioncheck.api;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.aaron.visioncheck.service.ReviewQueue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class ClassificationControllerTest {
    @Test void rejectsNonImageBytes() {
        var image = new MockMultipartFile("image", "not-image.png", "image/png", "not an image".getBytes());
        assertThrows(IllegalArgumentException.class, () -> new ClassificationController(new ReviewQueue()).classify(image));
    }

    @Test void rejectsMimeMismatchAndOversizedDimensionsBeforePixelDecode() throws Exception {
        BufferedImage tooWide = new BufferedImage(4001, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(tooWide, "png", png);
        var controller = new ClassificationController(new ReviewQueue());
        var mislabeled = new MockMultipartFile("image", "sample.jpg", "image/jpeg", png.toByteArray());
        var oversized = new MockMultipartFile("image", "wide.png", "image/png", png.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> controller.classify(mislabeled));
        assertThrows(IllegalArgumentException.class, () -> controller.classify(oversized));
    }

    @Test void returnsTopCandidatesAndQueuesMetadataOnlyForReview() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<2;y++) for (int x=0;x<2;x++) source.setRGB(x,y,0x2266CC);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var upload = new MockMultipartFile("image", "sample.png", "image/png", bytes.toByteArray());
        var queue = new ReviewQueue();
        var result = new ClassificationController(queue).classify(upload);
        assertEquals(3, result.candidates().size());
        assertEquals("REVIEW_REQUIRED", result.status());
        assertEquals(1, queue.pending().size());
        assertNotNull(queue.pending().getFirst().id());
    }
}
