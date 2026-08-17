package com.akshit.marketdata.feed;

import com.akshit.marketdata.core.FeedSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

public final class FileReplaySource implements FeedSource<String> {
    private final Stream<String> lines;
    private final Iterator<String> iterator;

    public FileReplaySource(Path path) throws IOException {
        this.lines = Files.lines(path);
        this.iterator = lines.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String next() {
        return iterator.next();
    }

    @Override
    public void close() {
        lines.close();
    }
}
