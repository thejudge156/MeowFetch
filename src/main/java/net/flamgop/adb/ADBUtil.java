package net.flamgop.adb;

import net.flamgop.LoggingLevel;
import net.flamgop.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ADBUtil {

    private static final ExecutorService EXECUTOR = Runtime.version().feature() >= 19 ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newSingleThreadExecutor();
    private static final String downloadUrl;

    public static boolean checkDaemon(@Nullable String adbPath) throws IOException {
        if (adbPath == null) adbPath = getAdbPath().toString();
        ProcessBuilder builder = new ProcessBuilder(adbPath, "devices")
            .redirectErrorStream(true)
            .redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        process.onExit().join();
        boolean startedSuccessfully = reader.lines().anyMatch(s -> s.toLowerCase().contains("* daemon started successfully")) || reader.lines().anyMatch(s -> s.toLowerCase().contains("list of devices attached"));
        reader.close();
        return process.exitValue() == 0 && startedSuccessfully;
    }

    public static @NotNull Pair<CompletableFuture<Void>, Process> logcat(@Nullable String adbPath, @NotNull LoggingLevel level, @NotNull String packageFilter, @NotNull OutputStream outputStream) throws IOException {
        if (adbPath == null) adbPath = getAdbPath().toString();
        CompletableFuture<Void> future = new CompletableFuture<>();
        ProcessBuilder builder = new ProcessBuilder(adbPath, "-d", "logcat", packageFilter + ":" + level.adbId(), "*:S")
            .redirectErrorStream(true)
            .redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        // transfer reader to outputStream
        EXECUTOR.submit(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.write((line + System.lineSeparator()).getBytes());
                    outputStream.flush();
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        process.onExit().whenComplete((proc, err) -> {
            if (proc.exitValue() != 0) {
                future.completeExceptionally(err);
            } else {
                future.complete(null);
            }
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        });
        return new Pair<>(future,process);
    }

    public static @NotNull Path getAdbPath() {
        String path = System.getenv("PATH");
        if (path.toLowerCase().contains("adb")) {
            String first = Arrays.stream(path.split(";")).filter(s -> s.contains("adb")).toList().getFirst();
            return Path.of(first);
        }
        File platformToolsPath = Path.of("./platform-tools/").toFile();
        try {
            if (!platformToolsPath.mkdir() && platformToolsPath.toPath().resolve("adb.exe").toFile().exists()) {
                return platformToolsPath.toPath().resolve("adb.exe");
            }

            downloadAndExtractZip(downloadUrl, Path.of("./"));

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return platformToolsPath.toPath().resolve("adb.exe");
    }

    private static void downloadAndExtractZip(@NotNull String url, @NotNull Path outputDir) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Create the request to download the zip file
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();

        // Send the request and get the response
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body());
                 ZipInputStream zis = new ZipInputStream(bais)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path filePath = outputDir.resolve(entry.getName());

                    // Ensure parent directories exist
                    if (entry.isDirectory()) {
                        Files.createDirectories(filePath);
                    } else {
                        Files.createDirectories(filePath.getParent());
                        try (OutputStream os = Files.newOutputStream(filePath)) {
                            zis.transferTo(os);
                        }
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Failed to download file. HTTP status code: " + response.statusCode());
        }
    }

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
        else if (osName.contains("mac")) downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip";
        else downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip"; // assume EVERYTHING else is linux.
    }
}
