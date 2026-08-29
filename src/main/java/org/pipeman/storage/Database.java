package org.pipeman.storage;

import java.time.LocalDateTime;
import java.util.List;

public interface Database {
    void upsertShaders(Iterable<Shader> shaders);

    List<Shader> listShadersToRender(int limit, int offset);

    record Shader(
            String id,
            String slug,
            String name,
            LocalDateTime lastUpdated,
            LocalDateTime scrapedAt,
            String latestVersion,
            String downloadURL,
            LocalDateTime imagesRenderedAt,
            String imagesRenderVersion
    ) {
    }
}
