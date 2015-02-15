package io.really.model;

import jdk.nashorn.internal.objects.NativeArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class HiddenFields {
    private ArrayList<String> hiddenFields = new ArrayList<>();
    public Consumer<NativeArray> hide = fields -> {
        Object[] objectArray = fields.asObjectArray();
        hiddenFields.addAll(Arrays.asList(Arrays.copyOf(objectArray, objectArray.length, String[].class)));
    };
    public String[] getHiddenFields() {
        String[] fieldsArray = new String[hiddenFields.size()];
        return hiddenFields.toArray(fieldsArray);
    }
}