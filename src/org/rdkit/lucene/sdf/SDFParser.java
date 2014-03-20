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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * This parser reads SDF Records from the passed in input stream. The records
 * get additionally the dataset name (property DATASET), the line number
 * (property LINE_NUMBER) and a record number (property RECORD_NUMBER) attached.
 * 
 * @author Manuel Schwarze
 */
public class SDFParser extends LineNumberReader {

	//
	// Members
	//

	/** The Dataset name to be attached to the records. Can be null. */
	private final String m_strDatasetName;

	/** True, if line numbers are getting recorded. False otherwise. */
	private final boolean m_bRecordLineNumbers;

	/** The record number that is getting attached to the next record to be delivered. */
	private int m_iRecordNumber;

	//
	// Constructor
	//

	/**
	 * Creates a new SDF parser that generates records with the specified
	 * dataset name (if not null), line numbers and an increasing record number
	 * that starts to count with the specified number.
	 * 
	 * @param strDatasetName Dataset name to be attached to the records. Can be null.
	 * @param in Input stream to read data from. Must not be null.
	 * @param iLineNumberStart First line number to start counting. If set to -1,
	 * 		this property will not be attached.
	 * @param iRecordNumberStart First record number to be delivered. If set to -1,
	 * 		this property will not be attached.
	 */
	public SDFParser(final String strDatasetName, final InputStream in,
			final int iLineNumberStart, final int iRecordNumberStart) {
		super(new InputStreamReader(in));

		m_bRecordLineNumbers = iLineNumberStart >= 0;
		if (m_bRecordLineNumbers) {
			setLineNumber(iLineNumberStart);
		}

		m_strDatasetName = strDatasetName;
		m_iRecordNumber = iRecordNumberStart;
	}

	//
	// Public Methods
	//

	/**
	 * Reads the next SDF Record from the underlying data stream.
	 */
	public synchronized SDFRecord readSdfRecord() throws IOException {
		SDFRecord record = null;

		final int iLineNumber = getLineNumber();
		final StringBuilder sbSdf = new StringBuilder(8192);
		String strLine = null;

		while ((strLine = readLine()) != null) {
			sbSdf.append(strLine).append("\r\n");
			if (strLine.startsWith("$$$$")) {
				record = new SDFRecord(sbSdf.toString());
				if (m_strDatasetName != null) {
					record.put(SDFRecord.PROPERTY_DATASET_NAME, m_strDatasetName);
				}
				if (m_bRecordLineNumbers) {
					record.put(SDFRecord.PROPERTY_LINE_NUMBER, iLineNumber);
				}
				if (m_iRecordNumber >= 0) {
					record.put(SDFRecord.PROPERTY_RECORD_NUMBER, m_iRecordNumber++);
				}
				break;
			}
		}

		return record;
	}
}
