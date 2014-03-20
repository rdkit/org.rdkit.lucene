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

import java.util.logging.Logger;

import org.RDKit.ROMol;
import org.RDKit.RWMol;
import org.RDKit.UInt_Vect;

/**
 * This class provides chemistry related utility functions.
 * 
 * @author Manuel Schwarze
 */
public final class ChemUtils {

	//
	// Constants
	//

	/** The logger instance. */
	protected static final Logger LOGGER = Logger.getLogger(ChemUtils.class.getName());

	//
	// Static Public Methods
	//

	/**
	 * Determines for the passed in string, if it is a valid Smiles string.
	 * 
	 * @param value Potential smiles string.
	 * 
	 * @return True, if the passed in string is Smiles compatible. False otherwise.
	 */
	public static boolean isSmiles(final String value) {
		boolean bRet = false;

		if (value != null) {
			try {
				final ROMol mol = RWMol.MolFromSmiles(value);
				mol.delete();
				bRet = true;
			}
			catch (final Exception e) {
				// Ignored by purpose
			}
		}

		return bRet;
	}

	/**
	 * Determines for the passed in string, if it is a valid CTab string.
	 * 
	 * @param value Potential CTab string.
	 * 
	 * @return True, if the passed in string is CTab compatible. False otherwise.
	 */
	public static boolean isCtab(final String value) {
		boolean bRet = false;

		if (value != null) {
			try {
				final ROMol mol = RWMol.MolFromMolBlock(value);
				mol.delete();
				bRet = true;
			}
			catch (final Exception e) {
				// Ignored by purpose
			}
		}

		return bRet;
	}

	/**
	 * Reverses the specified atom index list for the specified molecule.
	 * 
	 * @param mol Molecule with atoms. Can be null to return null.
	 * @param atomList Atom index list. Can be null to return a list with all atom indexes.
	 * 
	 * @return Atom index list that contains all atom indexes that are not in the specified list.
	 */
	public static UInt_Vect reverseAtomList(final ROMol mol, final UInt_Vect atomList) {
		UInt_Vect reverseList = null;

		if (mol != null) {
			final int iAtomCount = (int)mol.getNumAtoms();

			// Extreme case: No atoms in list => Reverse includes all
			if (atomList == null || atomList.size() == 0) {
				reverseList = new UInt_Vect(iAtomCount);
				for (int i = 0; i < iAtomCount; i++) {
					reverseList.set(i, i);
				}
			}

			// Normal case
			else {
				final int iInputSize = (int)atomList.size();
				int iDistinctInputCount = 0;
				final boolean[] arr = new boolean[iAtomCount];

				for (int i = 0; i < iInputSize; i++) {
					final int index = (int)atomList.get(i);
					if (index > 0 && index < arr.length) {
						if (arr[index] == false) {
							arr[index] = true;
							iDistinctInputCount++;
						}
					}
				}

				reverseList = new UInt_Vect(iAtomCount - iDistinctInputCount);
				int index = 0;
				for (int i = 0; i < iAtomCount; i++) {
					if (!arr[i]) {
						reverseList.set(index++, i);
					}
				}
			}
		}

		return reverseList;
	}

	//
	// Constructor
	//

	/**
	 * This constructor serves only the purpose to avoid instantiation of this class.
	 */
	private ChemUtils() {
		// To avoid instantiation of this class.
	}
}
