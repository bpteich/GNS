/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.commands.data;

import edu.umass.cs.gns.clientsupport.CommandResponse;
import edu.umass.cs.gns.commands.GnsCommand;
import edu.umass.cs.gns.commands.CommandModule;
import static edu.umass.cs.gns.clientsupport.Defs.*;
import edu.umass.cs.gns.clientsupport.FieldAccess;
import edu.umass.cs.gns.clientsupport.UpdateOperation;
import edu.umass.cs.gns.localnameserver.ClientRequestHandlerInterface;
import edu.umass.cs.gns.localnameserver.LocalNameServer;
import edu.umass.cs.gns.util.ResultValue;
import edu.umass.cs.gns.util.NSResponseCode;
import edu.umass.cs.gns.util.ValuesMap;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author westy
 */
public abstract class AbstractUpdate extends GnsCommand {

  public AbstractUpdate(CommandModule module) {
    super(module);
  }

  public abstract UpdateOperation getUpdateOperation();

  @Override
  public CommandResponse execute(JSONObject json, ClientRequestHandlerInterface handler) throws InvalidKeyException, InvalidKeySpecException,
          JSONException, NoSuchAlgorithmException, SignatureException {
    String guid = json.getString(GUID);
    String field = json.optString(FIELD, null);
    String value = json.optString(VALUE, null);
    String oldValue = json.optString(OLDVALUE, null);
    int index = json.optInt(N, -1);
    JSONObject userJSON = json.has(USERJSON) ? new JSONObject(json.getString(USERJSON)) : null;
    // writer might be unspecified so we use the guid
    String writer = json.optString(WRITER, guid);
    String signature = json.optString(SIGNATURE, null);
    String message = json.optString(SIGNATUREFULLMESSAGE, null);
    NSResponseCode responseCode;
    if (field == null) {
      // full JSON object update
      if (!(responseCode = LocalNameServer.getIntercessor().sendUpdateUserJSON(guid, new ValuesMap(userJSON), 
              getUpdateOperation(), writer, signature, message)).isAnError()) {
         return new CommandResponse(OKRESPONSE);
      } else {
        return new CommandResponse(BADRESPONSE + " " + responseCode.getProtocolCode());
      }
    } else {
      // single field update 
      if (!(responseCode = FieldAccess.update(guid, field,
              // special case for the ops which do not need a value
              value != null ? new ResultValue(Arrays.asList(value)) : new ResultValue(),
              oldValue != null ? new ResultValue(Arrays.asList(oldValue)) : null,
              index,
              getUpdateOperation(),
              writer, signature, message)).isAnError()) {
        return new CommandResponse(OKRESPONSE);
      } else {
        return new CommandResponse(BADRESPONSE + " " + responseCode.getProtocolCode());
      }
    }
  }
}
