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
package org.rdkit.lucene.sdf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringTokenizer;

/**
 * An SDF Record contains a structure and properties for this structure.
 * 
 * @author Manuel Schwarze
 */
public class SDFRecord extends LinkedHashMap<String, Object> {

	//
	// Constants
	//

	/** Serial number. */
	private static final long serialVersionUID = 4323371544247272054L;

	/** Property name for the data set name that this record belongs to. */
	public static final String PROPERTY_DATASET_NAME = "NOVARTIS_DATASET_NAME";

	/** Property name for the line number within the data set that this record belongs to. */
	public static final String PROPERTY_LINE_NUMBER = "NOVARTIS_LINE_NUMBER";

	/** Property name for the record number of this record. */
	public static final String PROPERTY_RECORD_NUMBER = "NOVARTIS_RECORD_NUMBER";

	//
	// Members
	//

	/** The structure contained in the SDF Record. */
	private String m_strStructure;

	//
	// Constructor
	//

	/**
	 * Creates a new SDF Record by parsing the passed in input
	 * taken from an SDF file. The text may end with $$$$.
	 * 
	 * @param strSdf Part of an SDF file, which encapsulates a single
	 * 		data record for a structure.
	 */
	public SDFRecord(final String strSdf) {
		load(strSdf);
	}

	//
	// Public Methods
	//

	/**
	 * Loads new data into this record from the passed in SDF Text.
	 * 
	 * @param strSdf SDF formatted data.
	 */
	public synchronized void load(String strSdf) {
		if (strSdf == null) {
			throw new IllegalArgumentException("SDF data must not be null.");
		}

		// Reset values
		m_strStructure = null;
		clear();

		// Load new values

		// Standardize new line characters
		strSdf = strSdf.replace("\r\n", "\n");

		// Check for empty molecule
		if (!strSdf.startsWith("> <")) {
			// Read molecule
			final int iIndexEnd = strSdf.indexOf("END");
			if (iIndexEnd > 0) {
				final StringBuilder sbMol = new StringBuilder(iIndexEnd + 10);
				sbMol.append(strSdf.substring(0, iIndexEnd + 3)).append("\n");
				m_strStructure = sbMol.toString();
				strSdf = strSdf.substring(iIndexEnd + 4); // Rest
			}
			else {
				m_strStructure = strSdf;
				strSdf = ""; // Rest
			}
		}

		// Read further properties
		strSdf = strSdf.replace("\n\n", "\n \n"); // Ensures that empty lines are not lost
		final StringTokenizer st = new StringTokenizer(strSdf, "\n");
		String strPropertyName = null;
		final StringBuilder sbPropertyValue = new StringBuilder(256);
		while (st.hasMoreTokens()) {
			final String strLine = st.nextToken().trim();

			// Found a new property name
			if (strLine.startsWith("> <") || strLine.startsWith(">  <")) {
				// Store old property
				if (strPropertyName != null) {
					addProperty(strPropertyName, sbPropertyValue);
				}

				// Find the beginning of the property name
				final int indexStart = strLine.indexOf("<", 2);
				if (indexStart == -1) {
					strPropertyName = null; // Error in SDF file
				}
				else {
					// Find the end of the property name
					final int indexEnd = strLine.indexOf(">", indexStart + 1);
					if (indexEnd == -1) {
						strPropertyName = null; // Error in SDF file
					}
					else {
						strPropertyName = strLine.substring(indexStart + 1, indexEnd);
					}
				}

				// Reset property value before reading the new one
				sbPropertyValue.setLength(0);
			}

			// Found the end of the SDF record
			else if (strLine.startsWith("$$$$")) {
				// Store old property
				if (strPropertyName != null) {
					addProperty(strPropertyName, sbPropertyValue);
				}
				break;
			}

			// Add to property value
			else {
				sbPropertyValue.append(strLine).append("\n");
			}
		}
	}

	/**
	 * Generates new SDF formated data based on the data of this record.
	 * 
	 * @return SDF formatted data.
	 */
	public synchronized String save() {
		return save(null);
	}

	/**
	 * Generates new SDF formated data based on the data of this record, but
	 * without writing out the properties specified in the parameter.
	 * 
	 * @param listExcludeProperties Optional list of properties that
	 * 		shall not be saved. Can be null.
	 * 
	 * @return SDF formatted data.
	 */
	public synchronized String save(final List<String> listExcludeProperties) {
		final StringBuilder sbSdf = new StringBuilder(8192);

		final String strStructure = getStructure();

		if (strStructure != null) {
			sbSdf.append(getStructure());
		}

		for (final String strPropertyName : keySet()) {
			if (listExcludeProperties == null ||
					!listExcludeProperties.contains(strPropertyName)) {
				sbSdf.append("\n").append("> <").append(strPropertyName).append('>');
				sbSdf.append(get(strPropertyName).toString());
			}
		}

		sbSdf.append("\r\n").append("$$$$");

		return sbSdf.toString();
	}

	/**
	 * Returns the molecule structure (Mol format).
	 * 
	 * @return Mol structure.
	 */
	public synchronized String getStructure() {
		return m_strStructure;
	}

	/**
	 * Returns the molecule structure (Mol format) with $$$$ at the end.
	 * 
	 * @return Mol structure.
	 */
	public synchronized String getStructureWithDollars() {
		return m_strStructure + "\r\n" + "$$$$";
	}

	/**
	 * Returns a string representation of this object.
	 * 
	 * @return String representation.
	 */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(1024);

		sb.append("SDFRecord { structure=\n")
		.append(m_strStructure)
		.append("\nproperties: ")
		.append(super.toString())
		.append("\n}");

		return sb.toString();
	}

	//
	// Private Methods
	//

	/**
	 * Adds a property parsed from the SDF text.
	 * 
	 * @param strPropertyName Property name. Can be null. In this case nothing gets added.
	 * @param sbPropertyValue Property value. Can be null or empty. In this case nothing gets added.
	 */
	private void addProperty(final String strPropertyName, final StringBuilder sbPropertyValue) {
		if (strPropertyName != null && sbPropertyValue != null) {
			// Remove new line characters from the end of a property value
			while (true) {
				int iLen = sbPropertyValue.length();
				if (iLen > 0) {
					final char ch = sbPropertyValue.charAt(iLen - 1);
					if (ch == '\n' || ch == '\r' || ch == '\t') {
						sbPropertyValue.setLength(--iLen);
					}
					else {
						break;
					}
				}
				else {
					break;
				}
			}

			if (sbPropertyValue.length() > 0) {
				put(strPropertyName, sbPropertyValue.toString());
			}
		}
	}
}
