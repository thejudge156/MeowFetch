package net.flamgop.adb;

import dadb.AdbShellStream;
import dadb.Dadb;
import net.flamgop.LoggingLevel;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ADBUtil {
    private static final ExecutorService EXECUTOR = Runtime.version().feature() >= 19 ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newSingleThreadExecutor();
    private static Dadb dadb;
    public static Optional<AdbShellStream> stream = Optional.empty();

    public static @NotNull CompletableFuture<Void> logcat(@NotNull LoggingLevel level, @NotNull String packageFilter, @NotNull OutputStream outputStream) throws IOException {
        CompletableFuture<Void> future = new CompletableFuture<>();
        stream = Optional.of(dadb.openShell("logcat --pid=$(pidof -s " + packageFilter +")"));
        // transfer reader to outputStream
        EXECUTOR.submit(() -> {
            try {
                while (stream.isPresent()) {
                    outputStream.write(stream.get().read().getPayload());
                    outputStream.flush();
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static Dadb findDevice()
    {
        return Dadb.discover();
    }

    public static void initADB() throws IllegalStateException {
        Dadb device = findDevice();
        if (device == null) {
            throw new IllegalStateException("Device not found");
        }
        dadb = device;
    }
}
