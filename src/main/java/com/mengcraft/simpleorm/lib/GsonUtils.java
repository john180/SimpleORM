package com.mengcraft.simpleorm.lib;

import com.google.common.collect.Maps;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.SneakyThrows;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mengcraft.simpleorm.ORM.nil;
import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class GsonUtils {

    private static final Field PRIMITIVE_VALUE = REFLECT_PRIMITIVE_VALUE();
    private static TypeFunctionRegistry<Object> registry = new TypeFunctionRegistry<>();

    static {
        registry.register(JsonNull.class, e -> null);
        registry.register(JsonPrimitive.class, e -> {
            try {
                return PRIMITIVE_VALUE.get(e);
            } catch (IllegalAccessException ignored) {
            }
            return null;
        });
        registry.register(JsonArray.class, e -> StreamSupport.stream(e.spliterator(), false).map(registry::handle).collect(Collectors.toList()));
        registry.register(JsonObject.class, e -> {
            Map<String, Object> container = Maps.newHashMap();
            for (Map.Entry<String, JsonElement> node : e.entrySet()) {
                container.put(node.getKey(), registry.handle(node.getValue()));
            }
            return container;
        });
    }

    @SneakyThrows
    private static Field REFLECT_PRIMITIVE_VALUE() {
        Field value = JsonPrimitive.class.getDeclaredField("value");
        value.setAccessible(true);
        return value;
    }

    public static Object dump(JsonElement value) {
        return registry.handle(value);
    }

    public static Gson createJsonInBuk() {
        return createJsonInBuk(null);
    }

    public static Gson createJsonInBuk(FieldNamingPolicy policy) {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeHierarchyAdapter(ConfigurationSerializable.class, new JsonSerializeAdapter());
        if (!nil(policy)) {
            b.setFieldNamingPolicy(policy);
        }
        return b.create();
    }

    public static class JsonSerializeAdapter implements JsonSerializer<ConfigurationSerializable>, JsonDeserializer<ConfigurationSerializable> {

        public JsonElement serialize(ConfigurationSerializable input, Type clz, JsonSerializationContext ctx) {
            return ctx.serialize(input.serialize());
        }

        public ConfigurationSerializable deserialize(JsonElement jsonElement, Type clz, JsonDeserializationContext ctx) throws JsonParseException {
            if (!jsonElement.isJsonObject()) {
                return null;
            }
            Tuple<Class, Object> tuple = tuple(Map.class, dump(jsonElement));
            try {
                return Reflector.object((Class<ConfigurationSerializable>) clz, tuple);
            } catch (Exception ignored) {
            }
            return Reflector.invoke(clz, "deserialize", tuple);
        }
    }

}
