package io.really.jshooks;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.really.model.ModelHookStatus.Terminated;
import io.really.model.ModelHookStatus.ValidationError;
import scala.Console;

public class API {
    public static BiConsumer<Integer, String> cancel =
            (Integer code, String message) -> {
                throw new ValidationError(new Terminated(code, message));
            };

    //todo should be replaced by a user-based log where the user can access that from the web interface
    public static Consumer<Object> print = (Object x) -> {
        String prefix = Console.YELLOW() + "JavaScript ~# >> " + Console.WHITE();
        if (x == null)
            System.out.println(prefix + x);
        else
            System.out.println(prefix + x.toString());
    };
}
