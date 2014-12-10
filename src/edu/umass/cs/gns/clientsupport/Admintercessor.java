/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.clientsupport;

import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.localnameserver.LNSListenerAdmin;
import edu.umass.cs.gns.localnameserver.LocalNameServer;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nsdesign.packet.admin.AdminRequestPacket;
import edu.umass.cs.gns.nsdesign.packet.admin.AdminResponsePacket;
import edu.umass.cs.gns.nsdesign.packet.admin.DumpRequestPacket;
import edu.umass.cs.gns.nsdesign.packet.admin.SentinalPacket;
import edu.umass.cs.gns.nsdesign.recordmap.NameRecord;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import static edu.umass.cs.gns.clientsupport.Defs.BADRESPONSE;
import edu.umass.cs.gns.localnameserver.ClientRequestHandlerInterface;
import static edu.umass.cs.gns.nsdesign.packet.Packet.getPacketType;
import edu.umass.cs.gns.util.Util;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implements some administrative functions for accessing the GNS.
 *
 * @author westy
 */
public class Admintercessor {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
  private static Random randomID;
 
  /**
   * Used for the general admin wait / notify handling
   */
  private static final Object adminResponseMonitor = new Object();
  /**
   * This is where the admin response results are put.
   */
  private static ConcurrentMap<Integer, JSONObject> adminResult;
  /**
   * Used for the dump wait / notify handling
   */
  private static final Object dumpMonitor = new Object();
  /**
   * This is where dump response records are collected while we're waiting for all them to come in.
   */
  private static ConcurrentMap<Integer, Map<Object, TreeSet<NameRecord>>> dumpStorage;
  /**
   * This is where the final dump response results are put once we see the sentinel packet.
   */
  private static ConcurrentMap<Integer, Map<Object, TreeSet<NameRecord>>> dumpResult;

  static {
    randomID = new Random();
    dumpStorage = new ConcurrentHashMap<Integer, Map<Object, TreeSet<NameRecord>>>(10, 0.75f, 3);
    dumpResult = new ConcurrentHashMap<Integer, Map<Object, TreeSet<NameRecord>>>(10, 0.75f, 3);
    adminResult = new ConcurrentHashMap<Integer, JSONObject>(10, 0.75f, 3);
  }
  
  /**
   * Clears the database and reinitializes all indices.
   *
   * @return
   */
  public static boolean sendResetDB(ClientRequestHandlerInterface handler) {
    try {
      sendAdminPacket(new AdminRequestPacket(AdminRequestPacket.AdminOperation.RESETDB).toJSONObject(), handler);
      return true;
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring this error while sending RESETDB request: " + e);
    }
    return false;
  }

  /**
   * Sends the delete all records command.
   * 
   * @return
   */
  public static boolean sendDeleteAllRecords(ClientRequestHandlerInterface handler) {
    try {
      sendAdminPacket(new AdminRequestPacket(AdminRequestPacket.AdminOperation.DELETEALLRECORDS).toJSONObject(), handler);
      return true;
    } catch (Exception e) {
      GNS.getLogger().warning("Error while sending DELETEALLRECORDS request: " + e);
    }
    return false;
  }

  /**
   * Sends the clear cache command.
   * 
   * @return
   */
  public static boolean sendClearCache(ClientRequestHandlerInterface handler) {
    try {
      sendAdminPacket(new AdminRequestPacket(AdminRequestPacket.AdminOperation.CLEARCACHE).toJSONObject(), handler);
      return true;
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error while sending CLEARCACHE request: " + e);
    }
    return false;
  }

  /**
   * Sends the dump cache command.
   * 
   * @return
   */
  public static String sendDumpCache(ClientRequestHandlerInterface handler) {
    int id = nextAdminRequestID();
    try {
      sendAdminPacket(new AdminRequestPacket(id, AdminRequestPacket.AdminOperation.DUMPCACHE).toJSONObject(), handler);
      waitForAdminResponse(id);
      JSONObject json = adminResult.get(id);
      if (json != null) {
        return json.getString("CACHE");
      } else {
        return null;
      }
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error while sending DUMPCACHE request: " + e);
      return null;
    }
  }

  /**
   * Sends the ping table command for a given node.
   * 
   * @param node
   * @return a string containing the ping results for the node
   */
  public static String sendPingTable(String node, ClientRequestHandlerInterface handler) {
    int id = nextAdminRequestID();
    try {
      sendAdminPacket(new AdminRequestPacket(id, AdminRequestPacket.AdminOperation.PINGTABLE, node).toJSONObject(), handler);
      waitForAdminResponse(id);
      JSONObject json = adminResult.get(id);
      if (json != null) {
        if (json.optString("ERROR", null) != null) {
          return BADRESPONSE + " " + json.getString("ERROR");
        }
        return json.getString("PINGTABLE");
      } else {
        return null;
      }
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error while sending PINGTABLE request: " + e);
      return null;
    }
  }

  /**
   * Sends the ping value command for node1 to node2 specified as integers. 
   * Returns the ping value between those nodes.
   * 
   * @param node1
   * @param node2
   * @return the ping value between those nodes
   */
  public static String sendPingValue(int node1, int node2, ClientRequestHandlerInterface handler) {
    return sendPingValue(Integer.toString(node1), Integer.toString(node1), handler);
  }

  /**
   * Sends the ping value command for node1 to node2 specified as strings. 
   * Returns the ping value between those nodes.
   * 
   * @param node1
   * @param node2
   * @return
   */
  public static String sendPingValue(String node1, String node2, ClientRequestHandlerInterface handler) {
    int id = nextAdminRequestID();
    try {
      sendAdminPacket(new AdminRequestPacket(id, AdminRequestPacket.AdminOperation.PINGVALUE, node1.toString(), 
              node2.toString()).toJSONObject(), handler);
      waitForAdminResponse(id);
      JSONObject json = adminResult.get(id);
      if (json != null) {
        if (json.optString("ERROR", null) != null) {
          return BADRESPONSE + " " + json.getString("ERROR");
        }
        return json.getString("PINGVALUE");
      } else {
        return null;
      }
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error while sending PINGVALUE request: " + e);
      return null;
    }
  }

  /**
   * Sends the change log level command.
   * 
   * @param level
   * @return
   */
  public static boolean sendChangeLogLevel(Level level, ClientRequestHandlerInterface handler) {
    try {
      AdminRequestPacket packet = new AdminRequestPacket(AdminRequestPacket.AdminOperation.CHANGELOGLEVEL, level.getName());
      sendAdminPacket(packet.toJSONObject(), handler);
      return true;
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error while sending CHANGELOGLEVEL request: " + e);
    }
    return false;
  }

  private static void waitForAdminResponse(int id) {
    try {
      GNS.getLogger().finer("Waiting for admin response id: " + id);
      synchronized (adminResponseMonitor) {
        while (!adminResult.containsKey(id)) {
          adminResponseMonitor.wait();
        }
      }
      GNS.getLogger().finer("Admin response id received: " + id);
    } catch (InterruptedException x) {
      GNS.getLogger().severe("Wait for return packet was interrupted " + x);
    }
  }

  /**
   * Processes incoming AdminResponse packets.
   * 
   * @param json
   */
  public static void handleIncomingAdminResponsePackets(JSONObject json) {
    try {
      switch (getPacketType(json)) {
        case ADMIN_RESPONSE:
          // put the records in dumpResult and let the folks waiting know they are ready
          try {
            AdminResponsePacket response = new AdminResponsePacket(json);
            int id = response.getId();
            GNS.getLogger().finer("Processing admin response for " + id);
            synchronized (adminResponseMonitor) {
              adminResult.put(id, response.getJsonObject());
              adminResponseMonitor.notifyAll();
            }
          } catch (JSONException e) {
            GNS.getLogger().warning("JSON error during admin response processing: " + e);
          } catch (ParseException e) {
            GNS.getLogger().warning("Parse error during admin response processing: " + e);
          }
          break;
      }
    } catch (JSONException e) {
      GNS.getLogger().warning("JSON error while getting packet type: " + e);
    }
  }

  // DUMP

  /**
   * Sends the dump command to the LNS.
   * 
   * @return
   */
    public static CommandResponse sendDump(ClientRequestHandlerInterface handler) {
    int id;
    if ((id = sendDumpOutputHelper(null, handler)) == -1) {
      return new CommandResponse(Defs.BADRESPONSE + " " + Defs.QUERYPROCESSINGERROR + " " + "Error sending dump command to LNS");
    }
    waitForDumpResponse(id);
    Map<Object, TreeSet<NameRecord>> result = dumpResult.get(id);
    dumpResult.remove(id);
    if (result != null) {
      return new CommandResponse(formatDumpRecords(result, handler));
    } else {
      return new CommandResponse(Defs.BADRESPONSE + " " + Defs.QUERYPROCESSINGERROR + " " + "No response to dump command!");
    }
  }

  private static void waitForDumpResponse(int id) {
    try {
      GNS.getLogger().finer("Waiting for dump response id: " + id);
      synchronized (dumpMonitor) {
        long timeoutExpiredMs = System.currentTimeMillis() + 10000;
        while (!dumpResult.containsKey(id)) {
          dumpMonitor.wait(timeoutExpiredMs - System.currentTimeMillis());
          if (System.currentTimeMillis() >= timeoutExpiredMs) {
            // we timed out... only got partial results{
            Map<Object, TreeSet<NameRecord>> recordsMap = dumpStorage.get(id);
            if (recordsMap != null) { // can be null if we timed out before getting any responses
              dumpResult.put(id, recordsMap);
              dumpStorage.remove(id);
            }
            break;
          }
        }
      }

      GNS.getLogger().finer("Dump response id received: " + id);
    } catch (InterruptedException x) {
      GNS.getLogger().severe("Wait for return packet was interrupted " + x);
    }
  }

  @SuppressWarnings("unchecked")
  private static String formatDumpRecords(Map<Object, TreeSet<NameRecord>> recordsMap, ClientRequestHandlerInterface handler) {
    // now process all the records we received

    StringBuilder result = new StringBuilder();
    // are there any NSs that didn't respond?
    Set<Object> missingIDs = new HashSet(handler.getGnsNodeConfig().getNodeIDs());
    missingIDs.removeAll(recordsMap.keySet());
    if (missingIDs.size() > 0) {
      result.append("Missing NSs: " + Util.setOfNodeIdToString(missingIDs));
      result.append(LINE_SEPARATOR);
    }
    // process all the entries into a nice string
    for (Map.Entry<Object, TreeSet<NameRecord>> entry : recordsMap.entrySet()) {
      result.append("Nameserver: " + entry.getKey().toString() +
              " (" + handler.getGnsNodeConfig().getNodeAddress(entry.getKey()).getHostName() + ")");
      result.append(LINE_SEPARATOR);
      for (NameRecord record : entry.getValue()) {
        try {
          result.append("  NAME: ");
          result.append(record.getName());
//        result.append(" / KEY: ");
//        result.append(record.getRecordKey().getName());
          result.append(" P: ");
          result.append(Util.setOfNodeIdToString(record.getPrimaryNameservers()));
//          result.append(" A: ");
//          result.append(record.getActiveNameServers().toString());
          result.append(" TTL: ");
          result.append(record.getTimeToLive());
          result.append(LINE_SEPARATOR);
          result.append("    VALUE: ");
          result.append(record.getValuesMap());
          result.append(LINE_SEPARATOR);
        } catch (FieldNotFoundException e) {
          GNS.getLogger().severe("FieldNotFoundException. " + e.getMessage());
        }
      }
    }
    return result.toString();
  }

  /**
   * Processes incoming Dump packets.
   * 
   * @param json
   */
  public static void handleIncomingDumpResponsePackets(JSONObject json) {
    try {
      switch (getPacketType(json)) {
        case DUMP_REQUEST:
          try {
            DumpRequestPacket dumpResponse = new DumpRequestPacket(json);
            int id = dumpResponse.getId();
            // grab or make a new recordsMap
            Map<Object, TreeSet<NameRecord>> recordsMap = dumpStorage.get(id);
            if (recordsMap == null) {
              recordsMap = new TreeMap<Object, TreeSet<NameRecord>>();
              dumpStorage.put(id, recordsMap);
            }
            // pull the records out of the dump response and put them in dumpStorage
            JSONArray jsonArray = dumpResponse.getJsonArray();
            // ServerID can now be a String or and Integer
            Object serverID = dumpResponse.getPrimaryNameServer();
            TreeSet<NameRecord> records = new TreeSet<NameRecord>();
            for (int i = 0; i < jsonArray.length(); i++) {
              records.add(new NameRecord(null, jsonArray.getJSONObject(i)));
            }
            recordsMap.put(serverID, records);
          } catch (JSONException e) {
            GNS.getLogger().warning("JSON error during dump reponse processing: " + e);
          }
          break;
        case SENTINAL:
          // put the records in dumpResult and let the folks waiting know they are ready
          try {
            SentinalPacket sentinal = new SentinalPacket(json);
            int id = sentinal.getId();
            GNS.getLogger().finer("Processing sentinel for " + id);
            synchronized (dumpMonitor) {
              dumpResult.put(id, dumpStorage.get(id));
              dumpStorage.remove(id);
              dumpMonitor.notifyAll();
            }
          } catch (JSONException e) {
            GNS.getLogger().warning("JSON error during dump sentinel processing: " + e);
          }
          break;
      }
    } catch (JSONException e) {
      GNS.getLogger().warning("JSON error while getting packet type: " + e);
    }
  }

  /**
   * Sends a command to collect all guids that contain the given tag.
   * 
   * @param tagName
   * @return
   */
  public static HashSet<String> collectTaggedGuids(String tagName, ClientRequestHandlerInterface handler) {
    int id;
    if ((id = sendDumpOutputHelper(tagName, handler)) == -1) {
      return null;
    }
    waitForDumpResponse(id);
    Map<Object, TreeSet<NameRecord>> result = dumpResult.get(id);
    dumpResult.remove(id);

    if (result != null) {
      HashSet<String> allGuids = new HashSet<String>();
      for (TreeSet<NameRecord> records : result.values()) {
        try {
          for (NameRecord record : records) {
            allGuids.add(record.getName());
          }
        } catch (FieldNotFoundException e) {
        }
      }
      return allGuids;
    } else {
      return null;
    }
  }

  private static int sendDumpOutputHelper(String tagName, ClientRequestHandlerInterface handler) {
    // send the request out to the local name server
    int id = nextDumpRequestID();
    GNS.getLogger().finer("Sending dump request id = " + id);
    try {
      sendAdminPacket(new DumpRequestPacket(id, 
              new InetSocketAddress(handler.getNodeAddress().getAddress(), GNS.DEFAULT_LNS_ADMIN_PORT), 
              tagName).toJSONObject(), handler);
    } catch (JSONException e) {
      GNS.getLogger().warning("Ignoring error sending DUMP request for id " + id + " : " + e);
      return -1;
    } catch (IOException e) {
      return -1;
    }
    return id;
  }

  private static void sendAdminPacket(JSONObject json, ClientRequestHandlerInterface handler) throws IOException {
    LNSListenerAdmin.handlePacket(json, null, handler);
  }

  private static int nextDumpRequestID() {
    int id;
    do {
      id = randomID.nextInt();
    } while (dumpResult.containsKey(id));
    return id;
  }

  private static int nextAdminRequestID() {
    int id;
    do {
      id = randomID.nextInt();
    } while (adminResult.containsKey(id));
    return id;
  }
}
