package io.really.model;

import jdk.nashorn.internal.objects.NativeArray;

import java.util.function.Consumer;

public interface PreUpdate {
    void preUpdate(String before, String after, String[] fields);
}