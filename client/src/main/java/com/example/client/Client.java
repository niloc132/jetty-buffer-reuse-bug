package com.example.client;


import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);


    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // Create and start HTTP2Client.
        HTTP2Client http2Client = new HTTP2Client();

        http2Client.start();

        // Connect to host.
        String host = "localhost";
        int port = 10000;
        CompletableFuture<Session> sessionPromise = http2Client.connect(new InetSocketAddress(host, port), new ServerSessionListener() {
        });
        // Obtain the client-side Session object.
        Session session = sessionPromise.get(5, TimeUnit.SECONDS);
        // Prepare the HTTP request headers.
        HttpFields.Mutable requestFields = HttpFields.build();
        // Prepare the HTTP request object.
        MetaData.Request request = new MetaData.Request("GET", HttpURI.from("http://" + host + ":" + port + "/connect"), HttpVersion.HTTP_2, requestFields);
        // Create the HTTP/ 2 HEADERS frame representing the HTTP request.
        HeadersFrame headersFrame = new HeadersFrame(request, null, false);
        // Prepare the listener to receive the HTTP response frames.
        Stream.Listener responseListener = new Stream.Listener() {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame) {
                System.err.println(frame);

                if (frame.isEndStream()) {
                    // Under the workaround, this is the last frame, so we can exit.
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Stream stream, int error, String reason, Throwable failure, Callback callback) {
                System.err.println(error + " " + reason);
                failure.printStackTrace();
            }

            @Override
            public void onDataAvailable(Stream stream) {
                // Read a chunk of the content.
                Stream.Data data = stream.readData();
                if (data == null) {
                    // No data available now, demand to be called back.
                    stream.demand();
                } else {
                    // Process the content.
                    System.out.println(data.frame().getByteBuffer().remaining() + " bytes read");
                    // Notify that the content has been consumed.
                    data.release();
                    if (!data.frame().isEndStream()) {
                        // Demand to be called back.
                        stream.demand();
                    }
                }
            }

            @Override
            public void onClosed(Stream stream) {
                System.err.println("closed");
            }

            @Override
            public void onReset(Stream stream, ResetFrame frame, Callback callback) {
                // Server said goodbye, log how it did so to validate this bug report and workaround
                System.err.println(frame);
                callback.succeeded();

                // Under the bug case, this lets the client main exit, after logging the cancel message.
                // One reasonable fix here would be for this to also log no_error and continue here.
                latch.countDown();
            }
        };
        // Send the HEADERS frame to create a stream.
        CompletableFuture<Stream> streamPromise = session.newStream(headersFrame, responseListener);
        Stream stream = streamPromise.get(5, TimeUnit.SECONDS);
        // Use the Stream object to send request content, if any, using a DATA frame.
        ByteBuffer content = StandardCharsets.UTF_8.encode("hello");
        DataFrame requestContent = new DataFrame(stream.getId(), content, false);
        stream.data(requestContent, Callback.NOOP);

        // Ask for data
        stream.demand();

        // Wait for the server to finish talking to us, stop the stream
        latch.await();

        // Done, stop the HTTP2Client.
        http2Client.stop();
    }
}
