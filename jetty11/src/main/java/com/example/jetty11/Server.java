package com.example.jetty11;

import com.example.servlet.AsyncStreamingServlet;
import org.eclipse.jetty.http2.parser.RateControl;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Server {

    public static void main(String[] args) throws Exception {
        org.eclipse.jetty.server.Server s = new org.eclipse.jetty.server.Server();
        HttpConfiguration httpConfiguration = new HttpConfiguration();

        HTTP2CServerConnectionFactory factory = new HTTP2CServerConnectionFactory(httpConfiguration);
        factory.setRateControlFactory(new RateControl.Factory() {});
//        ArrayByteBufferPool bufferPool = new ArrayByteBufferPool(
//                -1, -1, -1, -1,
//                -2,
//                -2);
//        ServerConnector sc = new ServerConnector(s, null, null, bufferPool, -1, -1, factory);
        ServerConnector sc = new ServerConnector(s, factory);
        sc.setPort(10000);

        s.setConnectors(new Connector[] { sc });

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        AsyncStreamingServlet servlet = new AsyncStreamingServlet();
        context.addServlet(new ServletHolder(servlet), "/connect");
        s.setHandler(context);

        s.start();
        s.join();
    }
}
