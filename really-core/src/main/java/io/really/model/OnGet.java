/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model;

import jdk.nashorn.internal.objects.NativeArray;

import java.util.function.Consumer;

public interface OnGet {
    void onGet(String auth, String obj, Consumer<NativeArray> hide);
}