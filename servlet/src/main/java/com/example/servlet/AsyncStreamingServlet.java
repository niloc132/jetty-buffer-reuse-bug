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

public class AsyncStreamingServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncStreamingServlet.class);


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AsyncContext asyncCtx = req.startAsync(req, resp);

        asyncCtx.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
//                LOG.info("onComplete");
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
//                LOG.info("onTimeout");
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
//                LOG.info("onError");
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
//                LOG.info("onStartAsync");
            }
        });

        ServletOutputStream outputStream = resp.getOutputStream();
        outputStream.setWriteListener(new WriteListener() {
            @Override
            public void onWritePossible() {
//                LOG.info("onWritePossible");
            }

            @Override
            public void onError(Throwable t) {
//                LOG.error("onError write", t);
            }
        });
        ServletInputStream inputStream = req.getInputStream();
        inputStream.setReadListener(new ReadListener() {
            @Override
            public void onDataAvailable() {
//                LOG.info("onDataAvailable");
            }

            @Override
            public void onAllDataRead() {
//                LOG.info("onAllDataRead");
            }

            @Override
            public void onError(Throwable t) {
//                LOG.error("onError read", t);
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
    }
}
