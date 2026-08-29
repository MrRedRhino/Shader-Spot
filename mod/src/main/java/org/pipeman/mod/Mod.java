package org.pipeman.mod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jdbi.v3.core.Jdbi;
import org.pipeman.mod.ImageProcessor.ImageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Mod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mod.class);
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .executor(SCHEDULER)
            .build();
    private static final Gson GSON = new Gson();
    private static final Jdbi JDBI = Jdbi.create("jdbc:postgresql://localhost:5432/shader_spot", "postgres", readPassword());
    private static CompletableFuture<DownloadResult> currentDownloadFuture;

    private static int taskIndex = 0;
    private static final List<Task> TASKS;
    private static final List<Preset> PRESETS;

    static {
        try {
            String shaders = Files.readString(Path.of("runtime/shaders.json"));
            TASKS = GSON.fromJson(shaders, new TypeToken<>() {
            });

            String presets = Files.readString(Path.of("runtime/presets.json"));
            PRESETS = GSON.fromJson(presets, new TypeToken<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onInitialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            minecraft.getWindow().setWidth(2560);
            minecraft.getWindow().setHeight(1440);
            if (!minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();

            currentDownloadFuture = downloadShader(getNextTask().orElseThrow());
            minecraft.createWorldOpenFlows().openWorld("Test World", () -> minecraft.gui.setScreen(new TitleScreen()));
        });
    }

    private static String readPassword() {
        try {
            return Files.readString(Path.of("../../secrets/postgres-password"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void worldLoaded() {
        wait(10, TimeUnit.SECONDS)
                .thenRun(() -> {
                    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

                    server.getLevel(Level.OVERWORLD).getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
                    server.getLevel(Level.OVERWORLD).getGameRules().set(GameRules.ADVANCE_TIME, false, server);

                    server.getPlayerList().getPlayersByUUID()
                            .get(Minecraft.getInstance().player.getUUID())
                            .setGameMode(GameType.SPECTATOR);
                })
                .thenRun(() -> handleNextShader(null));
    }

    private static void handleNextShader(String previousShaderName) {
        currentDownloadFuture
                .thenCompose(downloadResult -> {
                    if (previousShaderName != null) {
                        new File("shaderpacks", previousShaderName).delete();
                    }

                    Optional<Task> nextTask = getNextTask();
                    nextTask.ifPresent(task -> currentDownloadFuture = downloadShader(task));

                    Minecraft.getInstance().execute(() -> {
                        applyShader(downloadResult.filename());
                    });

                    return wait(4, TimeUnit.SECONDS)
                            .thenApply(_ -> new DownloadResultTaskPair(downloadResult, nextTask));
                })

                .thenCompose(pair -> {
                            Task task = pair.downloadResult().task();

                            return takeScreenshots(task.shaderID(), task.versionID())
                                    .thenRun(() -> {
                                        String filename = pair.downloadResult().filename();

                                        pair.nextTask().ifPresentOrElse(
                                                _ -> handleNextShader(filename),
                                                () -> System.exit(0)
                                        );
                                    });
                        }
                )

                .exceptionally(throwable -> {
                    LOGGER.error("Failed to load shader", throwable);
                    return null;
                });
    }

    private static void applyShader(String shaderName) {
        Iris.getIrisConfig().setShaderPackName(shaderName);
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(true);

        try {
            Iris.reload();
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload Iris!", e);
        }
    }

    private static CompletableFuture<Void> takeScreenshots(String shaderID, String versionID) {
        List<CompletableFuture<?>> uploads = new ArrayList<>();

        CompletableFuture<Void> iterator = iterateAsynchronously(PRESETS, preset -> {
            Minecraft minecraft = Minecraft.getInstance();
            assert minecraft.player != null;

            MinecraftServer server = minecraft.getSingleplayerServer();
            ServerPlayer serverPlayer = server.getPlayerList().getPlayersByUUID().get(minecraft.player.getUUID());

            ServerLevel level = server.getLevel(Level.OVERWORLD);
            serverPlayer.teleportTo(level,
                    preset.position().x(), preset.position().y(), preset.position().z(),
                    Set.of(), preset.rotation().x, preset.rotation().y, true);

            server.setWeatherParameters(0, 0, preset.rain(), false);

            Holder<DimensionType> dimensionType = level.dimensionTypeRegistration();
            level.clockManager().setTotalTicks(dimensionType.value().defaultClock().orElseThrow(), preset.daytime());

            return wait(4, TimeUnit.SECONDS)
                    .thenCompose(_ -> takeScreenshot())
                    .thenAccept(name -> uploads.add(uploadScreenshot(shaderID, name, preset.id())));
        });

        return iterator
                .thenCompose(_ -> CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new)))
                .thenRun(() -> markImagesRendered(shaderID, versionID));
    }

    private static CompletableFuture<Void> wait(long delay, TimeUnit unit) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        SCHEDULER.schedule(() -> future.complete(null), delay, unit);

        return future;
    }

    private static <T> CompletableFuture<Void> iterateAsynchronously(Collection<T> collection, Function<T, CompletableFuture<Void>> handler) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        handleIterateAsynchronously(collection.iterator(), handler, future);

        return future;
    }

    private static <T> void handleIterateAsynchronously(Iterator<T> iterator, Function<T, CompletableFuture<Void>> handler, CompletableFuture<Void> future) {
        try {
            handler.apply(iterator.next()).whenComplete((_, t) -> {
                if (t != null) future.completeExceptionally(t);
                else {
                    if (iterator.hasNext()) handleIterateAsynchronously(iterator, handler, future);
                    else future.complete(null);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private static CompletableFuture<String> takeScreenshot() {
        CompletableFuture<String> future = new CompletableFuture<>();

        Minecraft.getInstance().execute(() -> {
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
                try {
                    String name = UUID.randomUUID() + ".png";
                    image.writeToFile(Path.of(name));
                    future.complete(name);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            });
        });

        return future;
    }

    private static CompletableFuture<Void> uploadScreenshot(String shaderID, String screenshotPath, String presetID) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        SCHEDULER.execute(() -> {
            try {
                for (int i = 0; i < ImageType.values().length; i++) {
                    ImageProcessor.preprocessAndUpload(screenshotPath, ImageType.values()[i], presetID, shaderID);
                }

                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private static CompletableFuture<DownloadResult> downloadShader(Task task) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(task.downloadURL())).build();
        String filename = task.shaderID() + ".zip";

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofFile(Path.of("shaderpacks", filename)))
                .thenApply(_ -> new DownloadResult(task, filename));
    }

    private static Optional<Task> getNextTask() {
        if (taskIndex >= TASKS.size()) return Optional.empty();
        return Optional.of(TASKS.get(taskIndex++));
    }

    private static void markImagesRendered(String shaderID, String versionID) {
        JDBI.useHandle(h -> h.createUpdate("""
                        UPDATE shaders
                        SET images_rendered_at    = now(),
                            images_render_version = :version
                        WHERE id = :id
                        """)
                .bind("id", shaderID)
                .bind("version", versionID));
    }

    private record DownloadResult(Task task, String filename) {
    }

    private record DownloadResultTaskPair(DownloadResult downloadResult, Optional<Task> nextTask) {
    }

    public record Task(String shaderID, String downloadURL, String versionID) {
    }

    public record Preset(long daytime, boolean rain, Vec3 position, Vec2 rotation, String id) {
    }
}
