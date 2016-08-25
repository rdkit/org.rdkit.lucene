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
package org.rdkit.lucene.demo;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class AvalonSmilesIconFactory implements SmilesIconFactory {

	/** The Avalon Depictor URL for drawing SMILES. */
	public static final String DEFAULT_AVALON_URL = "http://localhost/depicter/mol-renderer/.png?smiles=%VALUE%&w=%WIDTH%&h=%HEIGHT%";

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(AvalonSmilesIconFactory.class.getName());

	//
	// Members
	//

	/** URL with placeholders for smiles, width and height of the desired picture. */
	private final String m_strUrl;
	/** Additions to URL for substructure highlighting */
	private final String m_strQueryUrl;

	//
	// Constructors
	//

	/**
	 * Creates  an Avalon Smiles Icon Factory with the default URL to links to localhost.
	 */
	public AvalonSmilesIconFactory() {
		this(DEFAULT_AVALON_URL);
	}

	/**
	 * Creates an Avalon Smiles Icon Factory with a specific URL.
	 * 
	 * @param strUrl The URL to call to download a PNG, GIF or JPG file. It must contain
	 * 		the following placeholders, which are replaced for every concrete call:
	 * 		%VALUE% - This is the (unencoded) SMILES value. %WIDTH% - The target width.
	 * 		%HEIGHT% - The target height.
	 */
	public AvalonSmilesIconFactory(final String strUrl) {
		m_strUrl = strUrl;
		m_strQueryUrl="&highlightSubstruct=true&smiles_highlight=%QUERY%";
	}

	//
	// Public Methods
	//

	@Override
	public Icon createSmilesIcon(final String strSmiles, final int width, final int height) {
		Icon icon = null;

		try {
			// Build URL for download - Note: Encoding of Smiles string is important!
			String strUrl = m_strUrl;
			strUrl = strUrl.replaceAll("%VALUE%", URLEncoder.encode(strSmiles, "UTF-8"));
			strUrl = strUrl.replaceAll("%WIDTH%", "" + width);
			strUrl = strUrl.replaceAll("%HEIGHT%", "" + height);

			// Open URL and get image
			icon = new ImageIcon(new URL(strUrl), strSmiles);
		}
		catch (final UnsupportedEncodingException excEnc) {
			LOGGER.log(Level.SEVERE, "Downloading a SMILES image from Avalon failed due to a misconfiguration with encoding.", excEnc);
		}
		catch (final MalformedURLException excBadUrl) {
			LOGGER.log(Level.SEVERE, "Downloading a SMILES image from Avalon failed due to a malformed URL. This might be a configuration issue.", excBadUrl);
		}

		return icon;
	}
	@Override
	public Icon createSmilesIcon(final String strSmiles, final int width, final int height, final String strQuery) {
		Icon icon = null;

		try {
			// Build URL for download - Note: Encoding of Smiles string is important!
			String strUrl = m_strUrl+m_strQueryUrl;
			strUrl = strUrl.replaceAll("%VALUE%", URLEncoder.encode(strSmiles, "UTF-8"));
			strUrl = strUrl.replaceAll("%WIDTH%", "" + width);
			strUrl = strUrl.replaceAll("%HEIGHT%", "" + height);
			strUrl = strUrl.replaceAll("%QUERY%", URLEncoder.encode(strQuery, "UTF-8"));


			// Open URL and get image
			icon = new ImageIcon(new URL(strUrl), strSmiles);
		}
		catch (final UnsupportedEncodingException excEnc) {
			LOGGER.log(Level.SEVERE, "Downloading a SMILES image from Avalon failed due to a misconfiguration with encoding.", excEnc);
		}
		catch (final MalformedURLException excBadUrl) {
			LOGGER.log(Level.SEVERE, "Downloading a SMILES image from Avalon failed due to a malformed URL. This might be a configuration issue.", excBadUrl);
		}

		return icon;
	}
}
