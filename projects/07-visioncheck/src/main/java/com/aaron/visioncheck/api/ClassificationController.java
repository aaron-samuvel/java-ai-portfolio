package com.aaron.visioncheck.api;

import com.aaron.visioncheck.service.ReviewQueue;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/classify")
public class ClassificationController {
    private static final long MAX_PIXELS = 12_000_000;
    private final ReviewQueue reviewQueue;
    public ClassificationController(ReviewQueue reviewQueue) { this.reviewQueue = reviewQueue; }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Classification classify(@RequestPart("image") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5_000_000) throw new IllegalArgumentException("Image must be between 1 byte and 5 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!contentType.equals("image/png") && !contentType.equals("image/jpeg")) {
            throw new IllegalArgumentException("Use a PNG or JPEG image.");
        }
        BufferedImage image = readBoundedImage(file, contentType);

        long[] rgb = sampleAverage(image);
        List<Candidate> candidates = rank(rgb[0], rgb[1], rgb[2]);
        String predicted = candidates.getFirst().label();
        var review = reviewQueue.enqueue(predicted, image.getWidth(), image.getHeight());
        return new Classification(review.id(), predicted, candidates, image.getWidth(), image.getHeight(),
                "REVIEW_REQUIRED", "Colour-score heuristic only; no trained classifier or image is retained. Confirm or correct the label.");
    }

    private BufferedImage readBoundedImage(MultipartFile file, String contentType) throws IOException {
        try (var raw = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(raw)) {
            if (input == null) throw new IllegalArgumentException("Unsupported or invalid image; use PNG or JPEG");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported or invalid image; use PNG or JPEG");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                boolean matchesMime = contentType.equals("image/png") ? format.equals("png") : format.equals("jpeg") || format.equals("jpg");
                if (!matchesMime) throw new IllegalArgumentException("Image content does not match its declared PNG/JPEG type");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4000 || height > 4000 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image must be at most 4000 by 4000 and 12 megapixels");
                }
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private long[] sampleAverage(BufferedImage image) {
        long r=0, g=0, b=0, n=0;
        int yStep=Math.max(1,image.getHeight()/100), xStep=Math.max(1,image.getWidth()/100);
        for(int y=0;y<image.getHeight();y+=yStep) for(int x=0;x<image.getWidth();x+=xStep) {
            int pixel=image.getRGB(x,y); r+=(pixel>>16)&255; g+=(pixel>>8)&255; b+=pixel&255; n++;
        }
        return new long[]{r/n,g/n,b/n};
    }

    private List<Candidate> rank(double r, double g, double b) {
        double max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        double red=r/255.0, green=g/255.0, blue=b/255.0;
        double saturation=max == 0 ? 0 : (max-min)/max;
        List<Candidate> scores=List.of(
                new Candidate("green-dominant scene", clamp(0.25+0.65*(green-Math.max(red,blue)))),
                new Candidate("blue-dominant scene", clamp(0.25+0.65*(blue-Math.max(red,green)))),
                new Candidate("warm-color dominant scene", clamp(0.25+0.65*(red-Math.max(green,blue)))),
                new Candidate("uncertain visual pattern", clamp(0.55-0.35*saturation)));
        return scores.stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()).limit(3).toList();
    }
    private double clamp(double score) { return Math.max(0.05, Math.min(0.95, score)); }

    public record Candidate(String label, double score) { }
    public record Classification(String reviewId, String label, List<Candidate> candidates, int width, int height,
                                 String status, String notice) { }
}
