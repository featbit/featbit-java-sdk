package co.featbit.commons.json;

/**
 * throws when a error occurs in serialization/deserialization of a ffc object
 */
public class JsonParseException extends RuntimeException {
    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonParseException(String message) {
        super(message);
    }
}
