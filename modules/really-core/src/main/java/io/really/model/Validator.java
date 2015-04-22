/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model;

import java.io.Serializable;

public interface Validator extends Serializable {
    boolean validate(String value);
}