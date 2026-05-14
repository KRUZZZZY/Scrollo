package revisionapp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonSupport() {
    }

    public static <T> T read(Path path, Class<T> type) throws IOException {
        return MAPPER.readValue(path.toFile(), type);
    }

    public static Map<String, Object> readMap(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), new TypeReference<>() {
        });
    }

    public static void write(Path path, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(path.toFile(), value);
    }
}
