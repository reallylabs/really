/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model;

public interface PreUpdate {
    void preUpdate(String before, String after, String[] fields);
}