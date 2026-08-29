package org.pipeman;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Modrinth {
    private static final Gson gson = new GsonBuilder()
            .create();
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "https://api.modrinth.com/v2";

    public static SearchResponse searchProjects(int offset, ProjectType type, Index index) throws IOException, InterruptedException {
        String facets = URLEncoder.encode("project_types = `" + type.id() + "`", StandardCharsets.UTF_8);
        String url = BASE_URL + "/search?new_filters=" + facets + "&index=" + index.id() + "&limit=100&offset=" + offset;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "MrRedRhino/Shader-Spot")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), SearchResponse.class);
    }

    public static Map<String, List<Version>> getVersions(Collection<String> ids) throws IOException, InterruptedException {
        String url = BASE_URL + "/versions?ids=" + URLEncoder.encode(gson.toJson(ids), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<Version> versions = gson.fromJson(response.body(), new TypeToken<>() {
        });
        return versions.stream().collect(Collectors.groupingBy(Version::project_id));
    }

    public static List<Project> getProjects(Collection<String> ids) throws IOException, InterruptedException {
        String url = BASE_URL + "/projects?ids=" + URLEncoder.encode(gson.toJson(ids), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return gson.fromJson(response.body(), new TypeToken<>() {
        });
    }

    public static Map<String, List<TeamMember>> getTeams(Collection<String> ids) throws IOException, InterruptedException {
        String url = BASE_URL + "/teams?ids=" + URLEncoder.encode(gson.toJson(ids), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<List<TeamMember>> teams = gson.fromJson(response.body(), new TypeToken<>() {
        });

        return teams.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(TeamMember::team_id));
    }

    public record SearchResponse(List<Hit> hits, int offset, int limit, int total_hits) {
    }

    public record Hit(
            String project_id,
            String title,
            String latest_version,
            long downloads,
            String project_type,
            String icon_url,
            String description,
            String slug,
            Date date_modified
    ) {
    }

    public record Version(String project_id, List<VersionFile> files, String id) {
    }

    public record Project(String id, String title, String description, String body, int downloads,
                          List<String> game_versions, String icon_url, List<String> loaders, int followers, String team,
                          List<String> versions) {
    }

    public record TeamMember(String team_id, User user) {
        public record User(String username) {
        }
    }

    public record VersionFile(String url) {
    }

    public enum ProjectType {
        MOD("mod"),
        RESOURCE_PACK("resourcepack"),
        DATA_PACK("datapack"),
        SHADER("shader"),
        MODPACK("modpack"),
        PLUGIN("plugin"),
        ;

        private final String type;

        ProjectType(String type) {
            this.type = type;
        }

        public String id() {
            return type;
        }
    }

    public enum Index {
        DATE_UPDATED("updated"),
        DATE_PUBLISHED("newest"),
        FOLLOWERS("follows"),
        DOWNLOADS("downloads"),
        RELEVANCE("relevance"),
        ;

        private final String id;

        Index(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
