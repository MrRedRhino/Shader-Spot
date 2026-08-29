package org.pipeman.mod;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.IOException;

public class ImageProcessor {
    private static final Storage storage = StorageOptions.getDefaultInstance().getService();

    public static void preprocessAndUpload(String file, ImageType imageType, String presetID, String shaderID) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("./runtime/ffmpeg",
                "-i", file,
                "-preset", "photo",
                "-vf", "scale=" + imageType.resolution(),
                "-qscale", String.valueOf(imageType.quality()),
                "-compression_level", "6",
                "-f", "webp",
                "-"
        ).start();

        String name = shaderID + "-" + presetID + "-" + imageType + ".webp";
        storage.createFrom(Blob.newBuilder("shader-spot-1", name).build(), process.getInputStream());
        assert process.waitFor() == 0 : "FFmpeg process failed";
    }

    public enum ImageType {
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
