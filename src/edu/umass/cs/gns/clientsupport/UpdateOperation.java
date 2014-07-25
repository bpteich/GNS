/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.clientsupport;

import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.util.ResultValue;
import edu.umass.cs.gns.util.ValuesMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * SINGLE_FIELD_CREATE - creates the field with the given value;<br>
 * SINGLE_FIELD_REPLACE_ALL - replaces the current values with this list;<br>
 * SINGLE_FIELD_REMOVE - deletes the values from the current values;<br>
 * SINGLE_FIELD_REPLACE_SINGLETON - treats the value as a singleton and replaces the current value with this one<br>
 * SINGLE_FIELD_CLEAR - deletes all the values from the field<br>
 * SINGLE_FIELD_APPEND - appends the given value onto the current values TREATING THE LIST AS A SET - meaning no duplicates<br>
 * SINGLE_FIELD_APPEND_OR_CREATE - an upsert operation similar to SINGLE_FIELD_APPEND that creates the field if it does not exist<br>
 * SINGLE_FIELD_REPLACE_ALL_OR_CREATE - an upsert operation similar to SINGLE_FIELD_REPLACE_ALL that creates the field if it does not exist<br>
 * SINGLE_FIELD_APPEND_WITH_DUPLICATION - appends the given value onto the current values TREATING THE LIST AS A LIST - duplicates are not removed<br>
 * SINGLE_FIELD_SUBSTITUTE - replaces all instances of the old value with the new value - if old and new are lists does a pairwise replacement<br>
 *
 * SINGLE_FIELD_REMOVE_FIELD - slightly different than the rest in that it actually removes the field
 * SINGLE_FIELD_SET - sets the element of the list specified by the argument index
 */
public enum UpdateOperation {

  //  Args:         singleField, skipread, upsert
  SINGLE_FIELD_CREATE(true, false, false),
  SINGLE_FIELD_REMOVE_FIELD(true, false, false),
  SINGLE_FIELD_CLEAR(true, false, false),
  SINGLE_FIELD_REPLACE_ALL(true, true, false), // currently the only skipRead op
  SINGLE_FIELD_REMOVE(true, false, false),
  SINGLE_FIELD_REPLACE_SINGLETON(true, false, false),
  SINGLE_FIELD_APPEND(true, false, false),
  SINGLE_FIELD_APPEND_OR_CREATE(true, false, true, SINGLE_FIELD_APPEND),
  SINGLE_FIELD_REPLACE_ALL_OR_CREATE(true, false, true, SINGLE_FIELD_REPLACE_ALL),
  SINGLE_FIELD_APPEND_WITH_DUPLICATION(true, false, false),
  SINGLE_FIELD_SUBSTITUTE(true, false, false),
  SINGLE_FIELD_SET(true, false, false),
  SINGLE_FIELD_SET_FIELD_NULL(true, false, false),
  //
  USER_JSON_REPLACE(false, false, false),
  USER_JSON_REPLACE_OR_CREATE(false, false, true, USER_JSON_REPLACE);
  //
  boolean singleFieldOperation;
  boolean ableToSkipRead;
  boolean upsert;
  UpdateOperation nonUpsertEquivalent = null;

  private UpdateOperation(boolean singleFieldOperation, boolean ableToSkipRead, boolean upsert) {
    this.singleFieldOperation = singleFieldOperation;
    this.ableToSkipRead = ableToSkipRead;
    this.upsert = upsert;
  }

  private UpdateOperation(boolean singleFieldOperation, boolean ableToSkipRead, boolean upsert, UpdateOperation nonUpsertEquivalent) {
    this.singleFieldOperation = singleFieldOperation;
    this.ableToSkipRead = ableToSkipRead;
    this.upsert = upsert;
    this.nonUpsertEquivalent = nonUpsertEquivalent;
  }

  public boolean isSingleFieldOperation() {
    return singleFieldOperation;
  }

  public boolean isAbleToSkipRead() {
    return ableToSkipRead;
  }

  public boolean isUpsert() {
    return upsert;
  }

  public UpdateOperation getNonUpsertEquivalent() {
    return nonUpsertEquivalent;
  }

  /**
   * Updates a valuesMap object based on the parameters given.
   *
   * If the key doesn't exist this will create it, but NameRecord code that calls this should still check for the key existing
   * in the name record so that the client can depend on that behavior for certain operations.
   *
   * @param valuesMap
   * @param key
   * @param newValues
   * @param oldValues
   * @param operation
   * @return false if the value was not updated true otherwise
   */
  public static boolean updateValuesMap(ValuesMap valuesMap, String key,
          ResultValue newValues, ResultValue oldValues, int argument,
          ValuesMap userJSON, UpdateOperation operation) {
    if (operation.isSingleFieldOperation()) {
      ResultValue valuesList = valuesMap.getAsArray(key);
      if (valuesList == null) {
        valuesList = new ResultValue();
      }
      if (updateValuesList(valuesList, key, newValues, oldValues, argument, operation)) {
        valuesMap.putAsArray(key, valuesList);
        return true;
      } else {
        return false;
      }
    } else {
      return true;
    }
  }

  private static boolean valuesListHasNullFirstElement(ResultValue valuesList) {
    return !valuesList.isEmpty() && valuesList.get(0).equals(Defs.NULLRESPONSE);
  }

  private static boolean updateValuesList(ResultValue valuesList, String key,
          ResultValue newValues, ResultValue oldValues, int argument,
          UpdateOperation operation) {
    switch (operation) {
      case SINGLE_FIELD_CLEAR:
        valuesList.clear();
        return true;
      case SINGLE_FIELD_CREATE:
      case SINGLE_FIELD_REPLACE_ALL_OR_CREATE:
      case SINGLE_FIELD_REPLACE_ALL:
        valuesList.clear();
        valuesList.addAll(newValues);
        return true;
      case SINGLE_FIELD_APPEND_WITH_DUPLICATION:
        // check for a null list and clear it if it is
        if (valuesListHasNullFirstElement(valuesList)) {
          valuesList.clear();
        }
        if (valuesList.addAll(newValues)) {
          return true;
        } else {
          return false;
        }
      case SINGLE_FIELD_APPEND_OR_CREATE:
      case SINGLE_FIELD_APPEND:
        Set singles; // use a hash to remove duplicates
        // check for a null list don't use the current values if it is
        if (valuesListHasNullFirstElement(valuesList)) {
          singles = new HashSet();
        } else {
          singles = new HashSet(valuesList);
        }
        singles.addAll(newValues);
        // clear the old values and
        valuesList.clear();
        // and the new ones
        valuesList.addAll(singles);
        return true;
      case SINGLE_FIELD_REMOVE:
        GNS.getLogger().fine("Remove " + newValues + "\tValues list = " + valuesList);
        // check for a null list reset it to empty and return false if it is
        if (valuesListHasNullFirstElement(valuesList)) {
          valuesList.clear();
          return false;
        }
        // otherwise remove all the values if they exists
        if (valuesList.removeAll(newValues)) {
          return true;
        } else {
          return false;
        }
      case SINGLE_FIELD_REPLACE_SINGLETON:
        valuesList.clear();
        if (!newValues.isEmpty()) {
          valuesList.add(newValues.get(0));
        }
        return true;
      case SINGLE_FIELD_SUBSTITUTE:
        // check for a null list reset it to empty and return false if it is
        if (valuesListHasNullFirstElement(valuesList)) {
          valuesList.clear();
          return false;
        }
        // otherwise do the substitue thing
        boolean changed = false;
        if (oldValues != null) {
          for (Iterator<Object> oldIter = oldValues.iterator(), newIter = newValues.iterator();
                  oldIter.hasNext() && newIter.hasNext();) {
            Object oldValue = oldIter.next();
            Object newValue = newIter.next();
            if (Collections.replaceAll(valuesList, oldValue, newValue)) {
              changed = true;
            }
          }
        }
        return changed;
      case SINGLE_FIELD_SET:
        // check for a null list reset it to empty and return false if it is
        if (valuesListHasNullFirstElement(valuesList)) {
          valuesList.clear();
          return false;
        }
        if (!newValues.isEmpty() && argument < valuesList.size()) {
          // ignoring anything more than the first element of the new values
          valuesList.set(argument, newValues.get(0));
        }
        return true;
      case SINGLE_FIELD_SET_FIELD_NULL:
        // already null return false
        if (valuesListHasNullFirstElement(valuesList)) {
          return false;
        }
        valuesList.clear();
        valuesList.add(Defs.NULLRESPONSE);
        return true;
      default:
        return false;
    }
  }
}
