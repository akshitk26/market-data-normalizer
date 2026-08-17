package com.akshit.marketdata.core;

import java.io.Closeable;

public interface FeedSource<T> extends Closeable {
    boolean hasNext();

    T next();

    @Override
    void close();
}
