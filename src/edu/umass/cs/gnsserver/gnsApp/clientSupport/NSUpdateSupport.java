/*
 * Copyright (C) 2016
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gnsserver.gnsApp.clientSupport;

import edu.umass.cs.gnscommon.exceptions.server.FailedDBOperationException;
import edu.umass.cs.gnscommon.exceptions.server.FieldNotFoundException;
import edu.umass.cs.gnscommon.exceptions.server.RecordNotFoundException;
import edu.umass.cs.gnsserver.activecode.ActiveCodeHandler;
import edu.umass.cs.gnsserver.database.ColumnFieldType;
import edu.umass.cs.gnsserver.gnsApp.AppReconfigurableNodeOptions;
import edu.umass.cs.gnsserver.gnsApp.GnsApplicationInterface;
import edu.umass.cs.gnsserver.gnsApp.NSResponseCode;
import edu.umass.cs.gnsserver.gnsApp.clientCommandProcessor.commandSupport.ActiveCode;
import edu.umass.cs.gnsserver.gnsApp.clientCommandProcessor.commandSupport.InternalField;
import edu.umass.cs.gnsserver.gnsApp.clientCommandProcessor.commandSupport.MetaDataTypeName;
import edu.umass.cs.gnsserver.gnsApp.clientCommandProcessor.commandSupport.UpdateOperation;
import edu.umass.cs.gnsserver.gnsApp.recordmap.BasicRecordMap;
import edu.umass.cs.gnsserver.gnsApp.recordmap.NameRecord;
import edu.umass.cs.gnsserver.main.GNS;
import edu.umass.cs.gnsserver.utils.ResultValue;
import edu.umass.cs.gnsserver.utils.ValuesMap;
import edu.umass.cs.utils.DelayProfiler;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.json.JSONException;

/**
 *
 * @author westy
 */
public class NSUpdateSupport {

  /**
   * Executes a local update operation.
   *
   * @param guid
   * @param field
   * @param writer
   * @param signature
   * @param message
   * @param operation
   * @param updateValue
   * @param oldValue
   * @param argument
   * @param userJSON
   * @param app
   * @param doNotReplyToClient
   * @return
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws JSONException
   * @throws IOException
   * @throws FailedDBOperationException
   * @throws RecordNotFoundException
   * @throws FieldNotFoundException
   */
  public static NSResponseCode executeUpdateLocal(String guid, String field,
          String writer, String signature, String message,
          UpdateOperation operation, ResultValue updateValue, ResultValue oldValue, int argument,
          ValuesMap userJSON, GnsApplicationInterface<String> app, boolean doNotReplyToClient)
          throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException,
          SignatureException, JSONException, IOException, FailedDBOperationException,
          RecordNotFoundException, FieldNotFoundException {
    if (AppReconfigurableNodeOptions.debuggingEnabled) {
      GNS.getLogger().info("Processing local update " + guid + " / " + field + "  " + operation
              + " " + updateValue);
    }
    Long authStartTime = System.currentTimeMillis();
    NSResponseCode errorCode = NSResponseCode.NO_ERROR;
    // FIXME : handle ACL checks for full JSON user updates
    if (writer != null && field != null) {
      // writer will be null for internal system reads
      errorCode = NSAuthentication.signatureAndACLCheck(guid, field, writer, signature, message, MetaDataTypeName.WRITE_WHITELIST, app);
    }
    DelayProfiler.updateDelay("totalUpdateAuth", authStartTime);
    // return an error packet if one of the checks doesn't pass
    if (errorCode.isAnError()) {
      return errorCode;
    }
    if (operation.equals(UpdateOperation.CREATE_INDEX)) {
      if (!updateValue.isEmpty() && updateValue.get(0) instanceof String) {
        if (AppReconfigurableNodeOptions.debuggingEnabled) {
          GNS.getLogger().info("Creating index for " + field + " " + updateValue);
        }
        app.getDB().createIndex(field, (String) updateValue.get(0));

        return NSResponseCode.NO_ERROR;
      } else {
        if (AppReconfigurableNodeOptions.debuggingEnabled) {
          GNS.getLogger().severe("Invalid index value:" + updateValue);
        }
        return NSResponseCode.ERROR;
      }
    } else {
      NameRecord nameRecord = getNameRecord(guid, field, operation, app.getDB());
      updateNameRecord(nameRecord, guid, field, operation, updateValue, oldValue, argument, userJSON,
              app.getDB(), app.getActiveCodeHandler());
      return NSResponseCode.NO_ERROR;
    }
  }

  private static NameRecord getNameRecord(String guid, String field, UpdateOperation operation, BasicRecordMap db) throws RecordNotFoundException, FailedDBOperationException {
    if (operation.isAbleToSkipRead()) {
      // some operations don't require a read first
      return new NameRecord(db, guid);
    } else //try {
     if (field == null) {
        return NameRecord.getNameRecord(db, guid);
      } else {
        return NameRecord.getNameRecordMultiField(db, guid, null, ColumnFieldType.LIST_STRING, field);
      }
  }

  private static void updateNameRecord(NameRecord nameRecord, String guid, String field,
          UpdateOperation operation, ResultValue updateValue, ResultValue oldValue, int argument,
          ValuesMap userJSON, BasicRecordMap db, ActiveCodeHandler activeCodeHandler) throws FailedDBOperationException, FieldNotFoundException {
    ValuesMap newValue = null;
    if (activeCodeHandler != null) {
      try {
        newValue = handleActiveCode(guid, field, userJSON, db, activeCodeHandler);
      } catch (JSONException e) {
        GNS.getLogger().severe("JSON problem while handling active code: " + e);
      }
    }
    if (newValue == null) {
      newValue = userJSON;
    }
    // END ACTIVE CODE HANDLING
    if (AppReconfigurableNodeOptions.debuggingEnabled && field != null) {
      GNS.getLogger().info("****** field= " + field + " operation= " + operation.toString() + " value= " + updateValue.toString() + " name Record=" + nameRecord.toString());
    }
    // Apply update to record in the database
    nameRecord.updateNameRecord(field, updateValue, oldValue, argument, newValue, operation);
  }

  private static ValuesMap handleActiveCode(String guid, String field, ValuesMap userJSON, BasicRecordMap db, ActiveCodeHandler activeCodeHandler) throws FailedDBOperationException, FieldNotFoundException, JSONException {
    // Only do active field handling for user fields.
    if (field == null || !InternalField.isInternalField(field)) {
      NameRecord activeCodeNameRecord = null;
      try {
        activeCodeNameRecord = NameRecord.getNameRecordMultiField(db, guid, null, ColumnFieldType.USER_JSON, ActiveCode.ON_WRITE);
      } catch (RecordNotFoundException e) {
      }
      if (AppReconfigurableNodeOptions.debuggingEnabled) {
        GNS.getLogger().info("AC--->>> " + activeCodeNameRecord.toString());
      }
      int hopLimit = 1;
      if (activeCodeNameRecord != null && activeCodeHandler.hasCode(activeCodeNameRecord, "write")) {
        String code64 = activeCodeNameRecord.getValuesMap().getString(ActiveCode.ON_WRITE);
        ValuesMap packetValuesMap = userJSON;
        if (AppReconfigurableNodeOptions.debuggingEnabled) {
          GNS.getLogger().info("AC--->>> " + guid + " " + field + " " + packetValuesMap.toReasonableString());
        }
        return activeCodeHandler.runCode(code64, guid, field, "write", packetValuesMap, hopLimit);
      }
    }
    return null;
  }

}