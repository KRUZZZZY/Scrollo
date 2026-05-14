package revisionapp;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public final class ScrolloLauncher {
    private ScrolloLauncher() {
    }

    public static void main(String[] args) {
        try {
            RevisionApplication.main(args);
        } catch (Throwable throwable) {
            logFatalStartupError(throwable);
            System.err.println("Scrollo failed to start.");
            System.err.println("Crash log: " + crashLogPath());
            throwable.printStackTrace(System.err);
            pauseIfDebugLauncher();
            System.exit(1);
        }
    }

    private static void logFatalStartupError(Throwable throwable) {
        try {
            Path log = crashLogPath();
            Files.createDirectories(log.getParent());
            Files.writeString(log, crashReport(throwable), StandardCharsets.UTF_8);
        } catch (IOException logFailure) {
            System.err.println("Could not write Scrollo crash log: " + logFailure.getMessage());
        }
    }

    private static Path crashLogPath() {
        return Path.of(userProfile(), ".revisionapp", "logs", "last-crash.log");
    }

    private static String crashReport(Throwable throwable) {
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        return """
                Scrollo startup failure
                Timestamp: %s
                User dir: %s
                Java version: %s
                Java home: %s

                %s
                """.formatted(
                LocalDateTime.now(),
                System.getProperty("user.dir"),
                System.getProperty("java.version"),
                System.getProperty("java.home"),
                stackTrace
        );
    }

    private static void pauseIfDebugLauncher() {
        if (!Boolean.getBoolean("scrollo.debug.pause")) {
            return;
        }
        System.err.println();
        System.err.println("Press Enter to close this debug window.");
        try {
            System.in.read();
        } catch (IOException ignored) {
            // Closing after a startup failure should not depend on console input working.
        }
    }

    private static String userProfile() {
        String profile = System.getenv("USERPROFILE");
        if (profile != null && !profile.isBlank()) {
            return profile;
        }
        return System.getProperty("user.home");
    }
}
