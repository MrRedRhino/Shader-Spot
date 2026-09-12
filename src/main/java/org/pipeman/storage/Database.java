package org.pipeman.storage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface Database {
    void upsertShaders(Iterable<Shader> shaders);

    List<Shader> listShadersToRender(int limit, int offset);

    Map<String, ShaderStatus> checkShaderStatus(List<String> ids);

    record ShaderStatus(String id, String renderedVersion) {
    }

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
