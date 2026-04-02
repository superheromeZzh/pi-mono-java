package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of {@link LsOperations}.
 */
public class LocalLsOperations implements LsOperations {

    @Override
    public List<LsEntry> list(Path directory) throws IOException {
        List<LsEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                String type;
                if (attrs.isSymbolicLink()) {
                    type = "symlink";
                } else if (attrs.isDirectory()) {
                    type = "directory";
                } else {
                    type = "file";
                }
                entries.add(new LsEntry(
                        path.getFileName().toString(),
                        type,
                        attrs.size(),
                        attrs.lastModifiedTime().toInstant()
                ));
            }
        }
        return entries;
    }
}
