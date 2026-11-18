package eu.fliegendewurst.triliumdroid.sync;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.io.*;
import java.util.*;

/** Reads one sync entity at a time, spooling base64 content outside the Java heap. */
public final class StreamingSyncReader {
    private StreamingSyncReader() {}

    public interface ChangeConsumer {
        void accept(Map<String, Object> change) throws Exception;
    }

    public static final class Summary {
        public final int lastEntityChangeId;
        public final int outstandingPullCount;
        public final int changeCount;

        Summary(int lastEntityChangeId, int outstandingPullCount, int changeCount) {
            this.lastEntityChangeId = lastEntityChangeId;
            this.outstandingPullCount = outstandingPullCount;
            this.changeCount = changeCount;
        }
    }

    public static Summary read(InputStream input, File spoolDirectory, ChangeConsumer consumer)
        throws Exception {
        int checkpoint = -1;
        int outstanding = -1;
        int count = 0;
        boolean sawChanges = false;
        List<File> files = new ArrayList<>();
        // Preserve large metadata strings accepted by the previous JSONObject reader.
        JsonFactory factory = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .build();
        try (JsonParser parser = factory.createParser(input)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "Expected sync response object");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME, "Expected response field");
                String name = parser.currentName();
                parser.nextToken();
                switch (name) {
                    case "lastEntityChangeId":
                        require(parser.currentToken() == JsonToken.VALUE_NUMBER_INT, "Invalid checkpoint");
                        checkpoint = parser.getIntValue();
                        break;
                    case "outstandingPullCount":
                        require(parser.currentToken() == JsonToken.VALUE_NUMBER_INT, "Invalid outstanding count");
                        outstanding = parser.getIntValue();
                        break;
                    case "entityChanges":
                        require(!sawChanges && parser.currentToken() == JsonToken.START_ARRAY, "Invalid changes array");
                        sawChanges = true;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            require(parser.currentToken() == JsonToken.START_OBJECT, "Expected entity change");
                            Map<String, Object> change = readObject(parser, spoolDirectory, files);
                            consumer.accept(change);
                            count++;
                            deleteFiles(files);
                        }
                        break;
                    default:
                        parser.skipChildren();
                }
            }
            require(sawChanges && checkpoint >= 0 && outstanding >= 0, "Incomplete sync response");
            require(parser.nextToken() == null, "Trailing sync response data");
            return new Summary(checkpoint, outstanding, count);
        } finally {
            deleteFiles(files);
        }
    }

    private static Map<String, Object> readObject(JsonParser parser, File directory, List<File> files)
        throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME, "Expected entity field");
            String name = parser.currentName();
            parser.nextToken();
            if (name.equals("content") && parser.currentToken() == JsonToken.VALUE_STRING) {
                File file = File.createTempFile("sync-content-", ".bin", directory);
                files.add(file);
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
                    parser.readBinaryValue(output);
                }
                result.put(name, file);
            } else {
                result.put(name, readValue(parser, directory, files));
            }
        }
        return result;
    }

    private static Object readValue(JsonParser parser, File directory, List<File> files) throws IOException {
        JsonToken token = parser.currentToken();
        require(token != null, "Truncated entity");
        switch (token) {
            case START_OBJECT: return readObject(parser, directory, files);
            case START_ARRAY:
                List<Object> list = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) list.add(readValue(parser, directory, files));
                return list;
            case VALUE_STRING: return parser.getText();
            case VALUE_NUMBER_INT: return parser.getLongValue();
            case VALUE_NUMBER_FLOAT: return parser.getDoubleValue();
            case VALUE_TRUE: return true;
            case VALUE_FALSE: return false;
            case VALUE_NULL: return null;
            default: throw new IOException("Unexpected entity token: " + token);
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    private static void deleteFiles(List<File> files) throws IOException {
        IOException failure = null;
        for (File file : files) {
            if (file.exists() && !file.delete()) failure = new IOException("Cannot remove sync spool file");
        }
        files.clear();
        if (failure != null) throw failure;
    }
}
