package com.example.client;


import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class Client {
    public static final String HOST = "localhost";
    public static final int PORT = 10000;
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);


    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // Create and start HTTP2Client.
        HTTP2Client http2Client = new HTTP2Client();

        http2Client.start();
        Instant start = Instant.now();
        Duration duration = Duration.ofSeconds(30);
        while (Instant.now().isBefore(start.plus(duration))) {
            try {
                // Connect to host.
                CompletableFuture<Session> sessionPromise = http2Client.connect(new InetSocketAddress(HOST, PORT), new ServerSessionListener() {
                });
                // Obtain the client-side Session object.
                Session session = sessionPromise.get(5, TimeUnit.SECONDS);

                for (int i = 0; i < 10; i++) {
                    sendRequest(session).get();
                }
            } catch (Exception e) {
                LOG.error("Error running client", e);
            }
        }
        System.exit(0);

    }

    private static Future<String> sendRequest(Session h2Session) throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = new CompletableFuture<>();
        // Prepare the HTTP request headers.
        HttpFields.Mutable requestFields = HttpFields.build();
        // Prepare the HTTP request object.
        MetaData.Request request = new MetaData.Request("GET", HttpURI.from("http://" + HOST + ":" + PORT + "/connect"), HttpVersion.HTTP_2, requestFields);
        // Create the HTTP/ 2 HEADERS frame representing the HTTP request.
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        // Prepare the listener to receive the HTTP response frames.
        Stream.Listener responseListener = new Stream.Listener() {
            @Override
            public void onFailure(Stream stream, int error, String reason, Throwable failure, Callback callback) {
                System.err.println(error + " " + reason);
                failure.printStackTrace();
                result.completeExceptionally(failure);
            }

            @Override
            public void onDataAvailable(Stream stream) {
                // Read a chunk of the content.
                Stream.Data data = stream.readData();
                if (data == null) {
                    // No data available now, demand to be called back.
                    stream.demand();
                } else {
                    // Treat this as the full payload - end the call and signal that the next can start
                    result.complete(StandardCharsets.UTF_8.decode(data.frame().getByteBuffer()).toString());
                    stream.reset(new ResetFrame(stream.getId(), ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP);
                }
            }

            @Override
            public void onClosed(Stream stream) {
//                System.err.println("closed");
                result.completeExceptionally(new RuntimeException("Stream closed"));
            }

            @Override
            public void onReset(Stream stream, ResetFrame frame, Callback callback) {
                // Server said goodbye, log how it did so to validate this bug report and workaround
                System.err.println(frame);
                callback.succeeded();
                result.completeExceptionally(new RuntimeException("Server reset the stream"));
            }
        };
        // Send the HEADERS frame to create a stream.
        CompletableFuture<Stream> streamPromise = h2Session.newStream(headersFrame, responseListener);
        Stream stream = streamPromise.get();
        // Use the Stream object to send request content, if any, using a DATA frame.
        ByteBuffer content = StandardCharsets.UTF_8.encode("hello");
        DataFrame requestContent = new DataFrame(stream.getId(), content, true);
        stream.data(requestContent, Callback.NOOP);

        // Ask for data
        stream.demand();

        return result;
    }
}
