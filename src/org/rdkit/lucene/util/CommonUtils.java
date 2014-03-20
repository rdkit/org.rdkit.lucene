/*
 * Copyright (C)2014, Novartis Institutes for BioMedical Research Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 * 
 * - Neither the name of Novartis Institutes for BioMedical Research Inc.
 *   nor the names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rdkit.lucene.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * This class offers several utility methods to deal with KNIME settings objects.
 * 
 * @author Manuel Schwarze
 */
public class CommonUtils {

	//
	// Constants
	//

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(CommonUtils.class.getName());

	/**
	 * This method compares two objects and considers also the value null.
	 * If the objects are both not null, equals is called for o1 with o2.
	 * 
	 * @param o1 The first object to compare. Can be null.
	 * @param o2 The second object to compare. Can be null.
	 * 
	 * @return True, if the two objects are equal. Also true, if
	 * 		   both objects are null.
	 */
	@SuppressWarnings("rawtypes")
	public static boolean equals(final Object o1, final Object o2) {
		boolean bResult = false;

		if (o1 == o2) {
			bResult = true;
		}
		else if (o1 != null && o1.getClass().isArray() &&
				o2 != null && o2.getClass().isArray() &&
				o1.getClass().getComponentType().equals(o2.getClass().getComponentType()) &&
				Array.getLength(o1) == Array.getLength(o2)) {
			final int iLength = Array.getLength(o1);

			// Positive presumption
			bResult = true;

			for (int i = 0; i < iLength; i++) {
				if ((bResult &= CommonUtils.equals(Array.get(o1, i), Array.get(o2, i))) == false) {
					break;
				}
			}
		}
		else if (o1 instanceof Collection && o2 instanceof Collection &&
				((Collection)o1).size() == ((Collection)o2).size()) {
			final Iterator i1 = ((Collection)o1).iterator();
			final Iterator i2 = ((Collection)o2).iterator();

			// Positive presumption
			if (i1.hasNext() && i2.hasNext()) {
				bResult = true;

				while (i1.hasNext() && i2.hasNext()) {
					if ((bResult &= CommonUtils.equals(i1.next(), i2.next())) == false) {
						break;
					}
				}
			}
		}
		else if (o1 != null && o2 != null) {
			bResult = o1.equals(o2);
		}
		else if (o1 == null && o2 == null) {
			bResult = true;
		}

		return bResult;
	}

	/**
	 * Determines the enumeration value based on a string.
	 * This string must match an enumeration name or toString() representation of the
	 * passed in enumeration class. If not, the default value will be used instead
	 * and a warning will be logged.
	 *
	 * @param enumType Enumeration class. Must not be null.
	 * @param valueAsString The new value to store.
	 * @param defaultValue Default enumeration value, if string cannot be recognized as
	 * 		any valid enumeration value.
	 * 
	 * @return Enumeration value, if found. If not found, it will return the
	 * 		specified defaultValue. If null was passed in as string, it will return null.
	 */
	public static <T extends Enum<T>> T getEnumValueFromString(final Class<T> enumType, final String valueAsString, final T defaultValue) {
		T retValue = null;

		if (valueAsString != null) {
			try {
				// First try: The normal "name" value of the enumeration
				retValue = Enum.valueOf(enumType, valueAsString);
			}
			catch (final Exception exc) {
				// Second try: The toString() value of an enumeration value - this comes handy when using FlowVariables
				for (final T enumValue : enumType.getEnumConstants()) {
					final String strRepresentation = enumValue.toString();
					if (valueAsString.equals(strRepresentation)) {
						retValue = enumValue;
						break;
					}
				}

				// Third case: Fallback to default value
				if (retValue == null) {
					LOGGER.warning("Value '" + valueAsString + "' could not be selected. " +
							"It is unknown in this version. Using default value '" + defaultValue + "'.");
					retValue = defaultValue;
				}
			}
		}

		return retValue;
	}

	//
	// Constructor
	//

	/**
	 * This constructor serves only the purpose to avoid instantiation of this class.
	 */
	private CommonUtils() {
		// To avoid instantiation of this class.
	}
}
