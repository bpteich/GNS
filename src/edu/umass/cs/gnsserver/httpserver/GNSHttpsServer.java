/*
 *
 *  Copyright (c) 2015 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): Westy
 *
 */
package edu.umass.cs.gnsserver.httpserver;

/**
 *
 * @author westy
 */
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.ClientRequestHandlerInterface;
import java.io.FileInputStream;
import java.net.BindException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 *
 * @author westy
 */
public class GNSHttpsServer extends GNSHttpServer {
  private HttpsServer httpsServer = null;

  private final static Logger LOG = Logger.getLogger(GNSHttpsServer.class.getName());

  /**
   *
   * @param port
   * @param requestHandler
   */
  public GNSHttpsServer(int port, ClientRequestHandlerInterface requestHandler) {
    super(port, requestHandler);
  }

  /**
   *
   */
  @Override
  public final void stop() {
    if (httpsServer != null) {
      httpsServer.stop(0);
    }
  }

  /**
   * Try to start the http server at the port.
   *
   * @param port
   * @return true if it was started
   */
  @Override
  public boolean tryPort(int port) {
    try {
      InetSocketAddress addr = new InetSocketAddress(port);
      httpsServer = HttpsServer.create(addr, 0);

      SSLContext sslContext = SSLContext.getInstance("TLS");

      // initialise the keystore
      char[] password = "qwerty".toCharArray();
      KeyStore keyStore = KeyStore.getInstance("JKS");
      FileInputStream inputStream = new FileInputStream("conf/keyStore/node100.jks");
      keyStore.load(inputStream, password);

      // setup the key manager factory
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
      keyManagerFactory.init(keyStore, password);

      // setup the trust manager factory
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
      trustManagerFactory.init(keyStore);

      // setup the HTTPS context and parameters
      sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
      httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        @Override
        public void configure(HttpsParameters parameters) {
          try {
            // initialise the SSL context
            SSLContext context = SSLContext.getDefault();
            SSLEngine engine = context.createSSLEngine();
            parameters.setNeedClientAuth(false);
            parameters.setCipherSuites(engine.getEnabledCipherSuites());
            parameters.setProtocols(engine.getEnabledProtocols());

            // get the default parameters
            SSLParameters defaultSSLParameters = context.getDefaultSSLParameters();
            parameters.setSSLParameters(defaultSSLParameters);

          } catch (NoSuchAlgorithmException e) {
            LOG.log(Level.SEVERE, "Failed to configure HTTPS service: " + e);
          }
        }
      });

      httpsServer.createContext("/", new EchoHandler());
      httpsServer.createContext("/" + GNS_PATH, new DefaultHandler());
      httpsServer.setExecutor(Executors.newCachedThreadPool());
      httpsServer.start();
      // Need to do this for the places where we expose the secure http service to the user
      requestHandler.setHttpsServerPort(port);

      LOG.log(Level.INFO,
              "HTTPS server is listening on port {0}", port);
      return true;
    } catch (BindException e) {
      LOG.log(Level.FINE,
              "HTTPS server failed to start on port {0} due to {1}",
              new Object[]{port, e});
      return false;
    } catch (IOException | NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException | KeyManagementException e) {
      LOG.log(Level.FINE,
              "HTTPS server failed to start on port {0} due to {1}",
              new Object[]{port, e});
      e.printStackTrace();
      return false;
    }
  }
}
