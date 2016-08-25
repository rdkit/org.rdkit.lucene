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
package org.rdkit.lucene.fingerprint;

import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.RDKit.ExplicitBitVect;
import org.RDKit.RDKFuncs;
import org.RDKit.ROMol;
import org.RDKit.RWMol;
import org.rdkit.lucene.bin.RDKit;

/**
 * A fingerprint factory is an object that knows how to produce fingerprints for SMILES.
 * It is used to calculate fingerprints for the search index as well as for query structures
 * when the index is searched. As some fingerprints, e.g. Avalon, support different
 * optimizations we have two different methods for the two different purposes.
 * 
 * @author Manuel Schwarze
 */
public class DefaultFingerprintFactory implements FingerprintFactory {

	//
	// Constants
	//

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(DefaultFingerprintFactory.class.getName());

	//
	// Members
	//

	/** The settings to be used for calculating structure fingerprints with this factory. */
	private final FingerprintSettings m_settingsStructureFps;

	/** The settings to be used for calculating query fingerprints with this factory. */
	private final FingerprintSettings m_settingsQueryFps;

	//
	// Constructors
	//

	/**
	 * Creates a new fingerprint factory based on the past in settings.
	 * Structure and query fingerprints are handled the same way. There is
	 * distinction between them. To handle them differently, use the other constructor.
	 * 
	 * @param settings Fingerprint settings. Must not be null.
	 */
	public DefaultFingerprintFactory(final FingerprintSettings settings) {
		if (settings == null) {
			throw new IllegalArgumentException("Fingerprint settings must not be null.");
		}

		m_settingsStructureFps = m_settingsQueryFps = settings;
	}

	/**
	 * Creates a new fingerprint factory based on the past in settings.
	 * Structure and query fingerprints are handled differently.
	 * 
	 * @param settingsStructureFps Fingerprint settings for structure fingerprints. Must not be null.
	 * @param settingsQueryFps Fingerprint settings for query fingerprints. Must not be null.
	 */
	public DefaultFingerprintFactory(final FingerprintSettings settingsStructureFps,
			final FingerprintSettings settingsQueryFps) {
		if (settingsStructureFps == null) {
			throw new IllegalArgumentException("Structure fingerprint settings must not be null.");
		}
		if (settingsQueryFps == null) {
			throw new IllegalArgumentException("Query fingerprint settings must not be null.");
		}

		m_settingsStructureFps = settingsStructureFps;
		m_settingsQueryFps = settingsQueryFps;
	}

	//
	// Public Methods
	//

	/**
	 * Returns the structure fingerprint settings this factory uses.
	 * 
	 * @return Structure fingerprint settings.
	 */
	public FingerprintSettings getStructureFpSettings() {
		return m_settingsStructureFps;
	}

	/**
	 * Returns the query fingerprint settings this factory uses.
	 * 
	 * @return Query fingerprint settings.
	 */
	public FingerprintSettings getQueryFpSettings() {
		return m_settingsQueryFps;
	}

	/**
	 * Creates a fingerprint based on the passed in SMILES.
	 * 
	 * @param strSmiles SMILES structure, preferably canonicalized by RDKit before. Must not be null.
	 * @param isCanonSmiles Set to true, if the SMILES was already canonicalized by RDKit.
	 * 
	 * @return Fingerprint as BitSet.
	 */
	@Override
	public BitSet createStructureFingerprint(final String strSmiles, final boolean isCanonSmiles) {
		return createFingerprint(strSmiles, isCanonSmiles, m_settingsStructureFps);
	}
	@Override
	public BitSet createStructureFingerprint(final ROMol mol){
		return createFingerprint(mol, m_settingsStructureFps);
	}

	/**
	 * Creates a fingerprint based on the passed in SMILES.
	 * 
	 * @param strSmiles SMILES structure, preferably canonicalized by RDKit before. Must not be null.
	 * @param isCanonSmiles Set to true, if the SMILES was already canonicalized by RDKit.
	 * 
	 * @return Fingerprint as BitSet.
	 */
	@Override
	public BitSet createQueryFingerprint(final String strSmiles, final boolean isCanonSmiles) {
		return createFingerprint(strSmiles, isCanonSmiles, m_settingsQueryFps);
	}
	public BitSet createQueryFingerprint(final ROMol mol){
		return createFingerprint(mol, m_settingsQueryFps);
	}

	//
	// Protected Methods
	//
	/**
	 * Creates a fingerprint based on the passed in SMILES.
	 * 
	 * @param strSmiles SMILES structure, preferably canonicalized by RDKit before. Must not be null.
	 * @param isCanonSmiles Set to true, if the SMILES was already canonicalized by RDKit.
	 * @param settings Fingerprint settings to be used.
	 * 
	 * @return Fingerprint as BitSet.
	 */
	protected BitSet createFingerprint(final String strSmiles, final boolean isCanonSmiles,
			final FingerprintSettings settings) {
		if (strSmiles == null) {
			throw new IllegalArgumentException("SMILES must not be null.");
		}

		BitSet fingerprint = null;
		final int iWaveId = RDKit.createUniqueCleanupWaveId();
		final int iLength = settings.getNumBits();

		try {
			// Exception: AvalonFP can directly be calculated from canonicalized SMILES
			if (settings.getRdkitFingerprintType() == FingerprintType.avalon && isCanonSmiles) {
				final ExplicitBitVect rdkitBitVector = RDKit.markForCleanup(new ExplicitBitVect(iLength), iWaveId);
				synchronized (FingerprintType.AVALON_FP_LOCK) {
					RDKFuncs.getAvalonFP(strSmiles, true, rdkitBitVector, iLength,
							settings.getAvalonQueryFlag() == 1, true /** resetVect */,
							settings.getAvalonBitFlags());
				}
				fingerprint = convert(rdkitBitVector);
			}

			// Normally: ROMol objects are needed to calculate fingerprints
			else {
				// Create an ROMol object
				ROMol mol;

				// Performance trick, if SMILES is already canonicalized
				if (isCanonSmiles) {
					mol = RDKit.markForCleanup(RWMol.MolFromSmiles(strSmiles, 0, false /** Do not sanitize */), iWaveId);
					mol.updatePropertyCache();
					RDKFuncs.fastFindRings(mol);
				}

				// Otherwise go the longer way
				else {
					mol = RDKit.markForCleanup(RWMol.MolFromSmiles(strSmiles, 0, true /** Sanitize */), iWaveId);
				}

				// Calculate fingerprint
				fingerprint = convert(RDKit.markForCleanup(
						settings.getRdkitFingerprintType().calculate(mol, settings), iWaveId));
			}
		}
		catch (final Exception exc) {
			LOGGER.log(Level.SEVERE, "Fingerprint calculation failed.", exc);
		}
		finally {
			RDKit.cleanupMarkedObjects(iWaveId);
		}

		return fingerprint;
	}
	/**
	 * Creates a fingerprint based on the passed in SMILES.
	 * 
	 * @param mol: rdkit molecule. Must not be null.
	 * @param settings Fingerprint settings to be used.
	 * 
	 * @return Fingerprint as BitSet.
	 */
	protected BitSet createFingerprint(final ROMol mol,
			final FingerprintSettings settings) {
		if (mol == null) {
			throw new IllegalArgumentException("molecule must not be null.");
		}

		BitSet fingerprint = null;
		final int iWaveId = RDKit.createUniqueCleanupWaveId();
		final int iLength = settings.getNumBits();

		try {
			// Exception: AvalonFP needs SMILES:
			if (settings.getRdkitFingerprintType() == FingerprintType.avalon ) {
				String strSmiles = RDKFuncs.MolToSmiles(mol,true);
				final ExplicitBitVect rdkitBitVector = RDKit.markForCleanup(new ExplicitBitVect(iLength), iWaveId);
				synchronized (FingerprintType.AVALON_FP_LOCK) {
					RDKFuncs.getAvalonFP(strSmiles, true, rdkitBitVector, iLength,
							settings.getAvalonQueryFlag() == 1, true /** resetVect */,
							settings.getAvalonBitFlags());
				}
				fingerprint = convert(rdkitBitVector);
			}
			else {
				// Calculate fingerprint
				fingerprint = convert(RDKit.markForCleanup(
						settings.getRdkitFingerprintType().calculate(mol, settings), iWaveId));
			}
		}
		catch (final Exception exc) {
			LOGGER.log(Level.SEVERE, "Fingerprint calculation failed.", exc);
		}
		finally {
			RDKit.cleanupMarkedObjects(iWaveId);
		}

		return fingerprint;
	}

	//
	// Private Methods
	//

	/**
	 * Converts an RDKit bit vector into a Java BitSet object.
	 * 
	 * @param rdkitBitVector RDKit (C++ based) bit vector. Can be null.
	 * 
	 * @return BitSet or null, if null was passed in.
	 */
	private BitSet convert(final ExplicitBitVect rdkitBitVector) {
		BitSet fingerprint = null;

		if (rdkitBitVector != null) {
			final int iLength = (int)rdkitBitVector.getNumBits();
			fingerprint = new BitSet(iLength);
			for (int i = 0; i < iLength; i++) {
				if (rdkitBitVector.getBit(i)) {
					fingerprint.set(i);
				}
			}
		}

		return fingerprint;
	}
}
