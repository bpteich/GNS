package edu.umass.cs.gns.nio;

import edu.umass.cs.gns.nsdesign.GNSNodeConfig;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 @author V. Arun
 */

/* This class helps emulate delays in NIO transport. It is mostly self-explanatory. 
 * TBD: Need to figure out how to use StartNameServer's emulated latencies.
 */
public class GNSDelayEmulator {

  private static boolean EMULATE_DELAYS = false;
  private static double VARIATION = 0.1; // 10% variation in latency
  private static boolean USE_CONFIG_FILE_INFO = false; // Enable this after figuring out how to use config file
  private static long DEFAULT_DELAY = 100; // 100ms
  private static GNSNodeConfig gnsNodeConfig = null; // node config object to get ping latencies for emulation.
  private static class DelayerTask extends TimerTask {

    JSONObject json;
    int destID;
    GNSNIOTransport nioTransport;

    public DelayerTask(GNSNIOTransport nioTransport, int destID, JSONObject json) {
      this.json = json;
      this.destID = destID;
      this.nioTransport = nioTransport;
    }

    @Override
    public void run() {
      try {
        nioTransport.sendToIDActual(destID, json);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void sendWithDelay(Timer timer, GNSNIOTransport niot, int id, JSONObject jsonData) throws IOException {
    if (GNSDelayEmulator.EMULATE_DELAYS) {
      DelayerTask dtask = new DelayerTask(niot, id, jsonData);
      timer.schedule(dtask, getDelay(id));
    } else {
      niot.sendToIDActual(id, jsonData);
    }
  }

  public static void emulateDelays() {
    GNSDelayEmulator.EMULATE_DELAYS = true;
  }

  public static void emulateConfigFileDelays(GNSNodeConfig gnsNodeConfig, double variation) {
    GNSDelayEmulator.EMULATE_DELAYS = true;
    GNSDelayEmulator.VARIATION = variation;
    GNSDelayEmulator.USE_CONFIG_FILE_INFO = true;
    GNSDelayEmulator.gnsNodeConfig = gnsNodeConfig;
  }

  private static long getDelay(int id) {
    long delay = 0;
    if (GNSDelayEmulator.EMULATE_DELAYS) {
      if (GNSDelayEmulator.USE_CONFIG_FILE_INFO) {
        delay = gnsNodeConfig.getPingLatency(id)/2;// divide by 2 for one-way delay
      } else {
        delay = GNSDelayEmulator.DEFAULT_DELAY;
      }
      delay = (long) ((1.0 + VARIATION * Math.random()) * delay);
    }
    return delay;
  }

  public static void main(String[] args) {
    System.out.println("Delay to node " + 3 + " = " + getDelay(3));
    System.out.println("There is no testing code for this class as it is unclear"
            + " how to access and use ConfigFileInfo. It is unclear what getPingLatency(id) even means."
            + " How does one specify the source node id?");
  }
}
