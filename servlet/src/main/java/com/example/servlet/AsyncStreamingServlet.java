package com.example.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AsyncStreamingServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncStreamingServlet.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AsyncContext asyncCtx = req.startAsync(req, resp);
        // Note that this will emit a stack trace on the server if the workaround is applied
//        asyncCtx.setTimeout(10_000);

        asyncCtx.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                LOG.info("onComplete");
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                LOG.info("onTimeout");
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                LOG.info("onError");
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                LOG.info("onStartAsync");
            }
        });

        ServletOutputStream outputStream = resp.getOutputStream();
        outputStream.setWriteListener(new WriteListener() {
            @Override
            public void onWritePossible() {
                LOG.info("onWritePossible");
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("onError", t);
            }
        });
        ServletInputStream inputStream = req.getInputStream();
        inputStream.setReadListener(new ReadListener() {
            @Override
            public void onDataAvailable() {
                LOG.info("onDataAvailable");
            }

            @Override
            public void onAllDataRead() {
                LOG.info("onAllDataRead");
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("onError", t);
            }
        });
        resp.setTrailerFields(() -> Map.of("foo", "1"));

        resp.setStatus(200);
        resp.setContentType("text/plain");

        if (outputStream.isReady()) {
            outputStream.print("Hello\n");
        }
        if (outputStream.isReady()) {
            resp.flushBuffer();
        }

        scheduler.schedule(() -> {
            try {
                if (outputStream.isReady()) {
                    outputStream.print("Goodbye\n");
                }
                if (outputStream.isReady()) {
                    resp.flushBuffer();
                }
                // This line causes the "bug", by producing a RST_STREAM(cancel) frame to send to the client
                asyncCtx.complete();
                // Workaround: comment out complete() above, and uncommment the following block. To avoid
                // a stack trace in logs after the close is finished, also remove the timeout above. This
                // results in an empty DATA frame with endStream=true being sent to the client, but note
                // that this isn't the only way to achieve correct behavior - the server could also send
                // RST_STREAM(no_error).

//                if (outputStream.isReady()) {
//                    outputStream.close();
//                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 3, java.util.concurrent.TimeUnit.SECONDS);
    }
}
