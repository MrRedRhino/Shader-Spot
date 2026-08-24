package org.pipeman;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.IOException;

public class ImageProcessor {
    private static final Storage storage = StorageOptions.newBuilder()
            .setProjectId("starlit-myth-402020")
            .build()
            .getService();

    public static void main(String[] args) throws IOException, InterruptedException {
        for (ImageType value : ImageType.values()) {
            preprocessAndUpload("0aa41b18-4403-495b-8be9-9e4fdf338d25.png", value);
        }
    }

    private static void preprocessAndUpload(String file, ImageType imageType) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("./runtime/ffmpeg",
                "-i", file,
                "-preset", "photo",
                "-vf", "scale=" + imageType.resolution(),
                "-qscale", String.valueOf(imageType.quality()),
                "-compression_level", "6",
                "-f", "webp",
                "-"
        ).start();
        storage.createFrom(Blob.newBuilder("shader-spot-1", imageType + ".webp").build(), process.getInputStream());

        assert process.waitFor() == 0 : "FFmpeg process failed";
    }

    private enum ImageType {
        HQ("2560:1440", 96),
        LQ("1920:1080", 50),
        THUMB("1280:720", 30);

        private final String resolution;
        private final int quality;

        ImageType(String resolution, int quality) {
            this.resolution = resolution;
            this.quality = quality;
        }

        public int quality() {
            return quality;
        }

        public String resolution() {
            return resolution;
        }
    }
}
