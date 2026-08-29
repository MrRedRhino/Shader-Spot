package org.pipeman.storage;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.statement.PreparedBatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PostgresDatabase implements Database {
    private final Jdbi jdbi = Jdbi.create("jdbc:postgresql://localhost:5432/shader_spot", "postgres", readPassword());

    public PostgresDatabase() {
        jdbi.registerRowMapper(ConstructorMapper.factory(Shader.class));
    }

    private String readPassword() {
        try {
            return Files.readString(Path.of("secrets/postgres-password"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void upsertShaders(Iterable<Shader> shaders) {
        jdbi.useHandle(h -> {
            PreparedBatch batch = h.prepareBatch("""
                    INSERT INTO shaders(id, slug, name, last_updated, scraped_at, latest_version, download_url, images_rendered_at,
                                        images_render_version)
                    VALUES (:s.id, :s.slug, :s.name, :s.lastUpdated, now(), :s.latestVersion, :s.downloadURL, null, null)
                    ON CONFLICT (id) DO UPDATE
                        SET slug           = excluded.slug,
                            name           = excluded.name,
                            last_updated   = excluded.last_updated,
                            scraped_at     = excluded.scraped_at,
                            latest_version = excluded.latest_version,
                            download_url   = excluded.download_url;
                    """);

            for (Shader shader : shaders) {
                batch.bindMethods("s", shader).add();
            }

            batch.execute();
        });
    }

    @Override
    public List<Shader> listShadersToRender(int limit, int offset) {
        return jdbi.withHandle(h -> h.createQuery("""
                        SELECT *
                        FROM shaders
                        WHERE images_rendered_at IS NULL
                           OR images_rendered_at < last_updated
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapTo(Shader.class)
                .list());
    }
}
