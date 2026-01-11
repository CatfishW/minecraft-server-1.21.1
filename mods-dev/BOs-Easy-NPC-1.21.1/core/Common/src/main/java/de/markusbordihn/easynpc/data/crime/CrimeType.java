/*
 * Copyright 2024 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.data.crime;

/**
 * Enum defining types of crimes that can trigger wanted level increases.
 */
public enum CrimeType {
  MERCHANT_KILL("merchant_kill", 2, 15),
  GUARD_KILL("guard_kill", 3, 25),
  THEFT("theft", 1, 5),
  ASSAULT("assault", 1, 10),
  TRESPASSING("trespassing", 1, 3);

  private final String id;
  private final int defaultWantedPenalty;
  private final int defaultPeacePenalty;

  CrimeType(String id, int defaultWantedPenalty, int defaultPeacePenalty) {
    this.id = id;
    this.defaultWantedPenalty = defaultWantedPenalty;
    this.defaultPeacePenalty = defaultPeacePenalty;
  }

  public String getId() {
    return this.id;
  }

  public int getDefaultWantedPenalty() {
    return this.defaultWantedPenalty;
  }

  public int getDefaultPeacePenalty() {
    return this.defaultPeacePenalty;
  }

  public static CrimeType fromId(String id) {
    for (CrimeType type : values()) {
      if (type.id.equals(id)) {
        return type;
      }
    }
    return null;
  }

  public static CrimeType get(String name) {
    try {
      return valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      return fromId(name);
    }
  }
}
