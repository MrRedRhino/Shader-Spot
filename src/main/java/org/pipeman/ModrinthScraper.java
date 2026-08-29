package org.pipeman;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.pipeman.storage.Database;
import org.pipeman.storage.PostgresDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public class ModrinthScraper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModrinthScraper.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        initialScrape();
        generateTasksFile(10);
    }

    private static void initialScrape() throws IOException, InterruptedException {
        PostgresDatabase database = new PostgresDatabase();

        int offset = 0;
        while (true) {
            LOGGER.info("Searching mods");
            Modrinth.SearchResponse response =
                    Modrinth.searchProjects(offset, Modrinth.ProjectType.SHADER, Modrinth.Index.DATE_PUBLISHED);

            List<String> ids = response.hits().stream()
                    .map(Modrinth.Hit::project_id)
                    .toList();

            List<String> versionIDs = Modrinth.getProjects(ids).stream()
                    .map(p -> p.versions().getFirst())
                    .toList();
            Map<String, List<Modrinth.Version>> versions = Modrinth.getVersions(versionIDs);

            List<Database.Shader> shaders = response.hits().stream()
                    .filter(h -> versions.get(h.project_id()) != null)
                    .map(h -> {
                        Modrinth.Version latestVersion = versions.get(h.project_id()).getFirst();

                        return new Database.Shader(h.project_id(),
                                h.slug(),
                                h.title(),
                                LocalDateTime.ofInstant(h.date_modified().toInstant(), ZoneId.of("Z")),
                                null,
                                latestVersion.id(),
                                latestVersion.files().getFirst().url(),
                                null,
                                null);
                    })
                    .toList();
            database.upsertShaders(shaders);

            LOGGER.info("Scraper progress: {} / {}", response.offset(), response.total_hits());
            if (response.offset() + response.limit() >= response.total_hits()) break;
            offset += 100;
        }
    }

    private static void generateTasksFile(int limit) throws IOException {
        PostgresDatabase database = new PostgresDatabase();
        JsonArray output = new JsonArray();
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        int offset = 0;
        while (true) {
            List<Database.Shader> shaders = database.listShadersToRender(Math.min(50, limit - offset), offset);
            if (shaders.isEmpty()) break;

            shaders.forEach(shader -> {
                JsonObject object = new JsonObject();
                object.addProperty("shaderID", shader.id());
                object.addProperty("versionID", shader.latestVersion());
                object.addProperty("downloadURL", shader.downloadURL());
                output.add(object);
            });
            LOGGER.info("Progress: {} / {}", offset += shaders.size(), limit);
        }

        Files.writeString(Path.of("shaders.json"), gson.toJson(output));
    }
}
