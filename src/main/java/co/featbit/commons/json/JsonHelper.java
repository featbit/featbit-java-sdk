package co.featbit.commons.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

/**
 * serialize or deserialize ffc object to/from json
 * this class is only for internal use
 */
public abstract class JsonHelper {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final Gson gson = new GsonBuilder()
            .setDateFormat(DATE_FORMAT)
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private static final String DATA_INVALID_ERROR = "Received Data invalid";

    private JsonHelper() {
        super();
    }

    /**
     * deserialize ffc object from json
     *
     * @param json        json string
     * @param objectClass object class
     * @param <T>
     * @return a ffc object
     * @throws JsonParseException
     */
    public static <T> T deserialize(String json, Class<T> objectClass) throws JsonParseException {
        try {
            return gson.fromJson(json, objectClass);
        } catch (Exception e) {
            throw new JsonParseException(DATA_INVALID_ERROR, e);
        }
    }

    /**
     * deserialize ffc object from json
     *
     * @param json json string
     * @param type object type
     * @param <T>
     * @return a ffc object
     * @throws JsonParseException
     */
    public static <T> T deserialize(String json, Type type) throws JsonParseException {
        try {
            return gson.fromJson(json, type);
        } catch (Exception e) {
            throw new JsonParseException(DATA_INVALID_ERROR, e);
        }
    }

    /**
     * deserialize ffc object from Json Reader
     *
     * @param reader      Json Reader
     * @param objectClass object class
     * @param <T>
     * @return a ffc object
     * @throws JsonParseException
     */
    public static <T> T deserialize(Reader reader, Class<T> objectClass) throws JsonParseException {
        try {
            return gson.fromJson(reader, objectClass);
        } catch (Exception e) {
            throw new JsonParseException(DATA_INVALID_ERROR, e);
        }
    }

    /**
     * deserialize ffc object from Json Reader
     *
     * @param reader Json Reader
     * @param type   object type
     * @param <T>
     * @return a ffc object
     * @throws JsonParseException
     */
    public static <T> T deserialize(Reader reader, Type type) throws JsonParseException {
        try {
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            throw new JsonParseException(DATA_INVALID_ERROR, e);
        }
    }

    /**
     * serialize to json
     *
     * @param o ffc object
     * @return a json string
     */
    public static String serialize(Object o) {
        return gson.toJson(o);
    }

    /**
     * interface to define action after deserialization - internal use
     */
    public interface AfterJsonParseDeserializable {
        void afterDeserialization();
    }

    /**
     * this class is used to apply {@link AfterJsonParseDeserializable} to a ffc object
     * see <a href="https://github.com/google/gson">gson</a>
     */
    public static final class AfterJsonParseDeserializableTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            return new AfterJsonParseDeserializableTypeAdapter(gson.getDelegateAdapter(this, typeToken));
        }
    }

    /**
     * this class is used to apply {@link AfterJsonParseDeserializable} to a ffc object
     * see <a href="https://github.com/google/gson">gson</a>
     */
    public static final class AfterJsonParseDeserializableTypeAdapter<T> extends TypeAdapter<T> {
        private final TypeAdapter<T> typeAdapter;

        public AfterJsonParseDeserializableTypeAdapter(TypeAdapter<T> typeAdapter) {
            this.typeAdapter = typeAdapter;
        }

        @Override
        public void write(JsonWriter jsonWriter, T t) throws IOException {
            typeAdapter.write(jsonWriter, t);
        }

        @Override
        public T read(JsonReader jsonReader) throws IOException {
            T res = typeAdapter.read(jsonReader);
            if (res instanceof AfterJsonParseDeserializable) {
                ((AfterJsonParseDeserializable) res).afterDeserialization();
            }
            return res;
        }
    }

}
