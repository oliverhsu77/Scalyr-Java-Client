/*
 * Scalyr client library
 * Copyright 2012 Scalyr, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scalyr.api;

import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

/**
 * Utilities for converting data from one type to another.
 *
 * Used to avoid type confusion when reading fields from a JSON object.
 */
public class Converter {
  /**
   * Convert any numeric type to Integer.
   * <p>
   * A null input is returned as-is. Non-numeric inputs trigger an exception. Out-of-range
   * values trigger undefined behavior.
   */
  public static Integer toInteger(Object value) {
    if (value instanceof Integer)
      return (Integer)value;
    else if (value instanceof Long)
      return (int)(long)(Long)value;
    else if (value instanceof Double)
      return (int)(double)(Double)value;
    else if (value == null)
      return null;
    else
      throw new RuntimeException("Can't convert [" + value + "] to Integer");
  }

  /**
   * Convert any numeric type to Long.
   * <p>
   * A null input is returned as-is. Non-numeric inputs trigger an exception. Out-of-range
   * values trigger undefined behavior.
   */
  public static Long toLong(Object value) {
    if (value instanceof Integer)
      return (long)(int)(Integer)value;
    else if (value instanceof Long)
      return (Long)value;
    else if (value instanceof Double)
      return (long)(double)(Double)value;
    else if (value == null)
      return null;
    else
      throw new RuntimeException("Can't convert [" + value + "] to Long");
  }

  /**
   * Convert any numeric type to Double.
   * <p>
   * A null input is returned as-is. Non-numeric inputs trigger an exception.
   */
  public static Double toDouble(Object value) {
    if (value instanceof Integer)
      return (double)(int)(Integer)value;
    else if (value instanceof Long)
      return (double)(long)(Long)value;
    else if (value instanceof Double)
      return (Double)value;
    else if (value == null)
      return null;
    else
      throw new RuntimeException("Can't convert [" + value + "] to Double");
  }

  /**
   * Return the given value coerced to Boolean.
   * <p>
   * A null input is returned as-is. Non-Boolean inputs trigger an exception.
   */
  public static Boolean toBoolean(Object value) {
    if (value instanceof Boolean)
      return (Boolean) value;
    else if (value == null)
      return null;
    else
      throw new RuntimeException("Can't convert [" + value + "] to Boolean");
  }

  /**
   * Return the given value converted to a String.
   * <p>
   * A null input is returned as-is.
   */
  public static String toString(Object value) {
    if (value != null)
      return value.toString();
    else
      return null;
  }

  /**
   * Converts certain pattern of String to disk/memory/network sizes.
   *
   * It takes a variety of values:
   *
   * 1000 // just number, default SI is bytes
   * 1KB  // 1000 bytes
   * 1KiB // 1024 bytes
   *
   * the accepted pattern is: [0-9]+[\s]*[[[KMGTP]i?]?B]?
   *
   *    [\s]*[0-9]+[\s]*[[[KMGT]i?]?B]?[\s]*
   *  0   1    2     3       4   5   6   7     0
   */
  public static Long parseNumberWithSI(Object valueWithSIObj) {
    String valueWithSI = toString(valueWithSIObj).trim().toUpperCase();
    long numberPart = 0;
    char multiplier = '\0';
    boolean withI = false;
    boolean negative = false;
    short state = 0;
    java.lang.String exceptionMessage = "Can't convert [" + valueWithSI + "]";
    for (int i = 0; i < valueWithSI.length(); i++) {
      char c = valueWithSI.charAt(i);
      if (i == 0 && c == '-') {
        negative = true;
      } else if (c >= '0' && c <= '9') {
        switch (state) {
          case 0:
          case 1:
          case 2: state = 2; numberPart *= 10; numberPart += c - '0'; break;
          default: throw new RuntimeException(exceptionMessage);
        }
      } else if (c == ' ') {
        switch (state) {
          case 0:
          case 1: state = 1; break;
          case 2:
          case 3: state = 3; break;
          case 4: state = 7; break;
          case 6:
          case 7: break;
          default: throw new RuntimeException(exceptionMessage);
        }
      } else if (c == 'K' || c == 'M' || c == 'G' || c == 'T' || c == 'P') {
        switch (state) {
          case 2:
          case 3: state = 4; multiplier = c; break;
          default: throw new RuntimeException(exceptionMessage);
        }
      } else if (c == 'I') {
        switch (state) {
          case 4: state = 5; withI = true; break;
          default: throw new RuntimeException(exceptionMessage);
        }
      } else if (c == 'B') {
        switch (state) {
          case 2:
          case 3:
          case 4:
          case 5: state = 6; break;
          default: throw new RuntimeException(exceptionMessage);
        }
      } else {
        throw new RuntimeException(exceptionMessage);
      }
    }
    if (negative) {
      numberPart *= -1;
    }
    if (state == 2 || state == 3) {
      return numberPart;
    } else if (state == 6 || state == 7 || state == 4) {
      long base = withI ? 1024 : 1000;
      switch (multiplier) {
        case '\0': return numberPart;
        case 'K': return numberPart * base;
        case 'M': return numberPart * base * base;
        case 'G': return numberPart * base * base * base;
        case 'T': return numberPart * base * base * base * base;
        case 'P': return numberPart * base * base * base * base * base;
      }
    }
    throw new RuntimeException(exceptionMessage);
  }

  /**
   * Parses a config string for a Duration Knob and returns its value in Nanoseconds.
   * See {@link com.scalyr.api.knobs.Knob.Duration} DurationKnob javadocs for usage/rules.
   */
  public static long parseNanos(String input) {
    input = input.trim(); // Eliminate leading/trailing spaces

    /*
     * State 0 = we're on number part
     * State 1 = units part
     */
    short state = 0;
    char c;
    boolean seenNumber = false;
    String magnitude = "";
    String units = "";
    final String exceptionMessage = "Invalid duration format: \"" + input + "\"";

    charLoop:
    for (int i = 0; i < input.length(); i++) {
      c = input.charAt(i);
      switch (state) {
        case 0: // Trying to parse number
          if (Character.isDigit(c)) {
            seenNumber = true;
          } else if (!seenNumber) { // If we've hit a non-# character before getting any numbers
            throw new RuntimeException(exceptionMessage);
          } else {
            magnitude = input.substring(0, i);
            i--; // So we don't skip over this first non-# character
            state = 1; // Moving onto units
          }
          break;

        case 1: // Trying to parse units
          if (!Character.isWhitespace(c)) { // If we hit a space, we do nothing this iteration
            units = input.substring(i).toLowerCase();
            if (timeUnitMap.containsKey(units)) { // If it's a valid unit format
              break charLoop;
            } else {
              throw new RuntimeException(exceptionMessage);
            }
          }
      }
    }

    return TimeUnit.NANOSECONDS.convert(java.lang.Long.parseLong(magnitude), timeUnitMap.get(units));
  }

  private static final Map<java.lang.String, TimeUnit> timeUnitMap = new HashMap<java.lang.String, TimeUnit>(){{
    put("ns"           , TimeUnit.NANOSECONDS  ) ;
    put("nano"         , TimeUnit.NANOSECONDS  ) ;
    put("nanos"        , TimeUnit.NANOSECONDS  ) ;
    put("nanosecond"   , TimeUnit.NANOSECONDS  ) ;
    put("nanoseconds"  , TimeUnit.NANOSECONDS  ) ;
    put("micro"        , TimeUnit.MICROSECONDS ) ;
    put("micros"       , TimeUnit.MICROSECONDS ) ;
    put("microsecond"  , TimeUnit.MICROSECONDS ) ;
    put("microseconds" , TimeUnit.MICROSECONDS ) ;
    put("\u03BC"       , TimeUnit.MICROSECONDS ) ; // µ, or very similar-looking
    put("\u03BCs"      , TimeUnit.MICROSECONDS ) ;
    put("\u00B5"       , TimeUnit.MICROSECONDS ) ; // µ, or very similar-looking
    put("\u00B5s"      , TimeUnit.MICROSECONDS ) ;
    put("ms"           , TimeUnit.MILLISECONDS ) ;
    put("milli"        , TimeUnit.MILLISECONDS ) ;
    put("millis"       , TimeUnit.MILLISECONDS ) ;
    put("millisecond"  , TimeUnit.MILLISECONDS ) ;
    put("milliseconds" , TimeUnit.MILLISECONDS ) ;
    put("s"            , TimeUnit.SECONDS      ) ;
    put("sec"          , TimeUnit.SECONDS      ) ;
    put("secs"         , TimeUnit.SECONDS      ) ;
    put("second"       , TimeUnit.SECONDS      ) ;
    put("seconds"      , TimeUnit.SECONDS      ) ;
    put("m"            , TimeUnit.MINUTES      ) ;
    put("min"          , TimeUnit.MINUTES      ) ;
    put("mins"         , TimeUnit.MINUTES      ) ;
    put("minute"       , TimeUnit.MINUTES      ) ;
    put("minutes"      , TimeUnit.MINUTES      ) ;
    put("h"            , TimeUnit.HOURS        ) ;
    put("hr"           , TimeUnit.HOURS        ) ;
    put("hrs"          , TimeUnit.HOURS        ) ;
    put("hour"         , TimeUnit.HOURS        ) ;
    put("hours"        , TimeUnit.HOURS        ) ;
    put("d"            , TimeUnit.DAYS         ) ;
    put("day"          , TimeUnit.DAYS         ) ;
    put("days"         , TimeUnit.DAYS         ) ;
  }};

}
