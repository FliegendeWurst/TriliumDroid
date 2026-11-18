package eu.fliegendewurst.triliumdroid.sync;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class StreamingSyncReaderTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void preservesMetadataStringsAboveJacksonDefaultLimit() throws Exception {
        File response = temp.newFile();
        File spool = temp.newFolder();
        int size = 21_000_000;
        byte[] block = new byte[8192];
        java.util.Arrays.fill(block, (byte) 'x');
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(response))) {
            out.write("{\"entityChanges\":[{\"entity\":{\"textRepresentation\":\"".getBytes(StandardCharsets.UTF_8));
            for (int left = size; left > 0;) {
                int n = Math.min(left, block.length);
                out.write(block, 0, n);
                left -= n;
            }
            out.write("\"}}],\"lastEntityChangeId\":1,\"outstandingPullCount\":0}".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = new FileInputStream(response)) {
            StreamingSyncReader.Summary summary = StreamingSyncReader.read(input, spool, change -> {
                String value = (String) ((Map<?, ?>) change.get("entity")).get("textRepresentation");
                assertEquals(size, value.length());
                for (int i = 0; i < size; i++) assertEquals('x', value.charAt(i));
            });
            assertEquals(1, summary.changeCount);
        }
        assertEquals(0, spool.list().length);
    }

    @Test public void decodesAttachmentLargerThanHeapWithoutMaterializingIt() throws Exception {
        File response = temp.newFile();
        File spool = temp.newFolder();
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[8192];
        for (int i = 0; i < block.length; i++) block[i] = (byte) i;
        int size = 391_643_136;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(response))) {
            out.write("{\"entityChanges\":[{\"entity\":{\"content\":\"".getBytes(StandardCharsets.UTF_8));
            OutputStream encoded = Base64.getEncoder().wrap(new FilterOutputStream(out) {
                @Override public void close() throws IOException { flush(); }
            });
            for (int left = size; left > 0;) {
                int n = Math.min(left, block.length);
                encoded.write(block, 0, n);
                expected.update(block, 0, n);
                left -= n;
            }
            encoded.close();
            out.write(("\",\"blobId\":\"large\"},\"entityChange\":{\"entityName\":\"blobs\"}}],"
                + "\"lastEntityChangeId\":42,\"outstandingPullCount\":0}").getBytes(StandardCharsets.UTF_8));
        }
        AtomicInteger changes = new AtomicInteger();
        try (InputStream in = new FileInputStream(response)) {
            StreamingSyncReader.Summary summary = StreamingSyncReader.read(in, spool, change -> {
                File content = (File) ((Map<?, ?>) change.get("entity")).get("content");
                assertEquals(size, content.length());
                MessageDigest actual = MessageDigest.getInstance("SHA-256");
                try (InputStream bytes = new FileInputStream(content)) {
                    int n;
                    while ((n = bytes.read(block)) != -1) actual.update(block, 0, n);
                }
                assertArrayEquals(expected.digest(), actual.digest());
                changes.incrementAndGet();
            });
            assertEquals(42, summary.lastEntityChangeId);
            assertEquals(0, summary.outstandingPullCount);
            assertEquals(1, summary.changeCount);
        }
        assertEquals(1, changes.get());
        assertEquals(0, spool.list().length);
    }

    @Test public void preservesNullEmptyBinaryAndUnknownMetadata() throws Exception {
        File spool = temp.newFolder();
        String json = "{\"unknown\":{\"nested\":[1,true]},\"outstandingPullCount\":3,"
            + "\"lastEntityChangeId\":9,\"entityChanges\":["
            + "{\"entity\":{\"content\":null,\"label\":\"한글\\n\\\"\",\"flag\":true}},"
            + "{\"entity\":{\"content\":\"\"}},"
            + "{\"entity\":{\"content\":\"AAEC\\/w==\"}},"
            + "{\"entity\":null,\"entityChange\":{\"isErased\":1}}]}";
        AtomicInteger index = new AtomicInteger();
        StreamingSyncReader.Summary summary = StreamingSyncReader.read(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), spool, change -> {
                int i = index.getAndIncrement();
                Map<?, ?> entity = (Map<?, ?>) change.get("entity");
                if (i == 0) {
                    assertNull(entity.get("content"));
                    assertEquals("한글\n\"", entity.get("label"));
                    assertEquals(true, entity.get("flag"));
                } else if (i == 1) {
                    assertEquals(0, ((File) entity.get("content")).length());
                } else if (i == 2) {
                    assertArrayEquals(new byte[]{0, 1, 2, -1}, Files.readAllBytes(((File) entity.get("content")).toPath()));
                } else assertNull(entity);
            });
        assertEquals(4, summary.changeCount);
        assertEquals(3, summary.outstandingPullCount);
        assertEquals(0, spool.list().length);
    }

    @Test public void removesSpoolOnTruncatedInputAndConsumerFailure() throws Exception {
        File spool = temp.newFolder();
        String partial = "{\"entityChanges\":[{\"entity\":{\"content\":\"AAEC";
        assertThrows(IOException.class, () -> StreamingSyncReader.read(
            new ByteArrayInputStream(partial.getBytes(StandardCharsets.UTF_8)), spool, c -> fail()));
        assertEquals(0, spool.list().length);
        String full = "{\"entityChanges\":[{\"entity\":{\"content\":\"AAEC\"}}],\"lastEntityChangeId\":1,\"outstandingPullCount\":0}";
        assertThrows(IOException.class, () -> StreamingSyncReader.read(
            new ByteArrayInputStream(full.getBytes(StandardCharsets.UTF_8)), spool,
            c -> { throw new IOException("storage failed"); }));
        assertEquals(0, spool.list().length);
    }

    @Test public void rejectsMissingCheckpointAndTrailingGarbage() throws Exception {
        File spool = temp.newFolder();
        for (String json : new String[]{"{\"entityChanges\":[]}",
            "{\"entityChanges\":[],\"lastEntityChangeId\":1,\"outstandingPullCount\":0} garbage"}) {
            assertThrows(IOException.class, () -> StreamingSyncReader.read(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), spool, c -> fail()));
        }
    }
}
