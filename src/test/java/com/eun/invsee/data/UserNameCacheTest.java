package com.eun.invsee.data;

import net.minecraft.server.players.NameAndId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserNameCacheTest {
    @TempDir Path directory;

    @Test
    void malformedRowsDoNotHideTheOtherOfflinePlayers() throws Exception {
        Path file = directory.resolve("usercache.json");
        Files.writeString(file, """
                [
                  {"name":"First","uuid":"00000000-0000-0000-0000-000000000001"},
                  null, 123, [], "bad row",
                  {"name":null,"uuid":"00000000-0000-0000-0000-000000000003"},
                  {"name":{},"uuid":"00000000-0000-0000-0000-000000000003"},
                  {"name":"Broken","uuid":null},
                  {"name":"Broken","uuid":"invalid"},
                  {"name":"  ","uuid":"00000000-0000-0000-0000-000000000003"},
                  {"name":"Second","uuid":"00000000-0000-0000-0000-000000000002"}
                ]
                """);

        assertEquals(List.of("First", "Second"), read(file).stream().map(NameAndId::name).toList());
    }

    @Test
    void unreadableOrInvalidFilesStillReturnAnEmptySnapshot() throws Exception {
        Path file = directory.resolve("usercache.json");
        assertTrue(read(file).isEmpty());
        Files.writeString(file, "{broken");
        assertTrue(read(file).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<NameAndId> read(Path file) throws Exception {
        Method method = UserNameCache.class.getDeclaredMethod("read", Path.class);
        method.setAccessible(true);
        return (List<NameAndId>) method.invoke(null, file);
    }
}
