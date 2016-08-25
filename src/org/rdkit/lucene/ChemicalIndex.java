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
package org.rdkit.lucene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.RDKit.GenericRDKitException;
import org.RDKit.RDKFuncs;
import org.RDKit.ROMol;
import org.RDKit.RWMol;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.Version;
import org.rdkit.lucene.bin.RDKit;
import org.rdkit.lucene.fingerprint.FingerprintFactory;
import org.rdkit.lucene.sdf.SDFParser;
import org.rdkit.lucene.sdf.SDFRecord;

public class ChemicalIndex {

	//
	// Constants
	//

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(ChemicalIndex.class.getName());

	public static final Version LUCENE_VERSION = Version.LUCENE_36;

	/** Field name of the primary key for indexed molecules. */
	public static final String FIELD_PK = "pk";

	/** Field name of the canonicalized SMILES. */
	public static final String FIELD_SMILES = "smiles";

	/** Field name of the fingerprint. */
	public static final String FIELD_FP = "fp";

	/** Field name of molecule names (synonyms). */
	public static final String FIELD_NAME = "name";

	/** Empty results. */
	private static final String[] EMPTY_RESULTS = new String[0];

	//
	// Members
	//

	private boolean m_bShutdown;

	private final Directory m_directory;

	private final AnalyzerFactory m_analyzerFactory;

	private final FingerprintFactory m_fingerprintFactory;

	private final IndexWriterConfigFactory m_configFactory;

	private IndexWriter m_writer;

	private IndexSearcher m_searcher;

	private final List<IndexListener> m_lListener;

	private final Object m_lockWriter = new Object(); // TODO: Used to block reading operations when writing

	private final Object m_lockSearcher = new Object(); // TODO: Used to block writing operations when reading

	//
	// Constructor
	//

	/**
	 * Creates a new Chemical Index object, which works on the specified
	 * directory.
	 * 
	 * @param directory
	 *            Index directory. Must not be null.
	 * @param analyzerFactory
	 *            Analyzer factory to be used for searcher initialization as
	 *            well as index writer initialization, if not config object is passed in.
	 *            Must not be null.
	 * @param fingerprintFactory
	 * 		      Fingerprint factory to be used to create fingerprints for
	 * 			  structures when indexing or searching. Must not be null.
	 * 			  Note: If the logic behind fingerprinting changes, it is required
	 * 			  to rebuild the entire index.
	 * @param configFactory
	 *            Configuration of index writer. Can be null to create standard config.
	 */
	public ChemicalIndex(final Directory directory, final AnalyzerFactory analyzerFactory,
			final FingerprintFactory fingerprintFactory, final IndexWriterConfigFactory configFactory) {
		// Pre-checks
		if (directory == null) {
			throw new IllegalArgumentException(
					"Index directory must not be null.");
		}
		if (analyzerFactory == null) {
			throw new IllegalArgumentException(
					"Analyzer factory must not be null.");
		}
		if (fingerprintFactory == null) {
			throw new IllegalArgumentException(
					"Fingerprint factory must not be null.");
		}

		if (!RDKit.activate()) {
			throw new UnsatisfiedLinkError("RDKit library could not be loaded.");
		}

		// Assign members
		m_bShutdown = false;
		m_directory = directory;
		m_analyzerFactory = analyzerFactory;
		m_fingerprintFactory = fingerprintFactory;
		m_configFactory = (configFactory == null ? new DefaultIndexWriterConfigFactory(analyzerFactory) : configFactory);
		m_writer = null;
		m_searcher = null;
		m_lListener = new ArrayList<IndexListener>();
	}

	//
	// Public Methods
	//

	/**
	 * Add an index listener, if it is was not registered before.
	 * 
	 * @param l
	 *            Index listener to be added.
	 */
	public void addIndexListener(final IndexListener l) {
		synchronized (m_lListener) {
			if (!m_lListener.contains(l)) {
				m_lListener.add(l);
			}
		}
	}

	/**
	 * Removes an index listener.
	 * 
	 * @param l
	 *            Index listener to be removed.
	 */
	public void removeIndexListener(final IndexListener l) {
		synchronized (m_lListener) {
			m_lListener.remove(l);
		}
	}

	/**
	 * Adds the specified SDF file to the index.
	 * 
	 * @param sdfFile SDF File. Must not be null.
	 * @param strFieldPrimaryKey The field name that holds the primary key. Must not be null.
	 * @param strIgnoreUpToPK Start indexing on that primery key. Ignore the ones before. Can be null.
	 * @param setIgnorePKs Set of primary keys with structures that shall not be indexed. Can be null.
	 * 
	 * @throws IOException
	 */
	public void addSDFFileToIndex(final File sdfFile, final String strFieldPrimaryKey,
			final String strIgnoreUpToPK, final Set<String> setIgnorePKs) throws IOException {
		// Pre-checks
		if (sdfFile == null) {
			throw new IllegalArgumentException("The SDF File must not be null.");
		}
		if (strFieldPrimaryKey == null) {
			throw new IllegalArgumentException("The primary key field of the SDF File must not be null.");
		}

		int iTotalErrors = 0;
		int iSubsequentialErrors = 0;

		InputStream in = new FileInputStream(sdfFile);
		try {
			final String strFileName = sdfFile.getName();
			if (strFileName.endsWith(".gz")
					|| strFileName.endsWith(".zip")) {
				in = new GZIPInputStream(in);
			}
			final SDFParser parser = new SDFParser(sdfFile.getName(), in, 1, 0);
			SDFRecord molSdf = null;
			boolean bStartAdding = (strIgnoreUpToPK == null);

			while ((molSdf = parser.readSdfRecord()) != null) {
				String strPK = null;

				try {
					final Object objPK = molSdf.get(strFieldPrimaryKey);
					if (objPK != null) {
						strPK = objPK.toString();

						if (bStartAdding && (setIgnorePKs == null || !setIgnorePKs.contains(strPK))) {
							final String strStructure = molSdf.getStructure();
							if (strStructure != null) {
								addMoleculeAsSDF(strPK, molSdf, null, molSdf);
							}
							else {
								LOGGER.log(Level.WARNING, "No structure found for primary key '" +
										strPK + "' not found. Ignoring.");
							}
						}
						else if (strPK.equals(strIgnoreUpToPK)) {
							bStartAdding = true;
						}
					}
					else {
						strPK = " at line " + molSdf.get(SDFRecord.PROPERTY_LINE_NUMBER);
						throw new IllegalArgumentException("Primary key field '" +
								strFieldPrimaryKey + "' not found.");
					}

					iSubsequentialErrors = 0;
				}
				catch (final Exception exc) {
					iSubsequentialErrors++;
					iTotalErrors++;
					LOGGER.log(Level.SEVERE, "Molecule " + strPK + " could not be added to index.", exc);

					if (iSubsequentialErrors > 100) {
						throw new IOException("Too many errors in a row. Giving up.", exc);
					}

					if (exc instanceof IOException) {
						throw (IOException)exc;
					}
				}
			}
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (final IOException exc) {
					// Ignored
				}
			}
		}
		if (iTotalErrors > 0) {
			LOGGER.log(Level.SEVERE, iTotalErrors + " molecules could not be added due to errors.");
		}
	}
	/**
	 * Notifies all index listener when a molecule has been added.
	 * 
	 * @param strPK
	 *            Primary key of the molecule. Can be null.
	 * @param strSmiles
	 *            Canonicalized SMILES of the molecule. Can be null.
	 */
	public void onMoleculeAdded(final String strPK, final String strSmiles) {
		synchronized (m_lListener) {
			try {
				for (int i = 0; i < m_lListener.size(); i++) {
					m_lListener.get(i).onMoleculeAdded(strPK, strSmiles);
				}
			}
			catch (final Exception exc) {
				LOGGER.log(Level.SEVERE, "Notifying index listener failed.", exc);
			}
		}
	}

	/**
	 * Closes writer and searcher of this index. This may take some time, if
	 * merges are currently running. Writer and searcher will be recreated on
	 * demand. To avoid this, call {@link #shutdown()} instead.
	 * 
	 * @throws IOException
	 *             Thrown, if closing of searcher or writer failed.
	 */
	public void close() throws IOException {
		if (m_searcher != null) {
			m_searcher.close();
			m_searcher = null;
		}
		if (m_writer != null) {
			m_writer.close(true);
			m_writer = null;
		}
	}

	/**
	 * Shutdown of this index. Writer and searcher will be closed. After this
	 * call they cannot be reopen again. To reopen, you will need to instantiate
	 * another ChemicalIndex object.
	 * 
	 * @throws IOException
	 *             Thrown, if closing of searcher or writer failed.
	 */
	public void shutdown() throws IOException {
		m_bShutdown = true;
		close();
	}

	/**
	 * Returns true, if this index object has been shutdown and cannot be used
	 * anymore.
	 * 
	 * @return True, if shutdown() was called before. False otherwise.
	 */
	public boolean isShutdown() {
		return m_bShutdown;
	}

	/**
	 * Adds the specified molecule with the specified primary key and registers
	 * also the specified names (if not null) for the index. If a molecule with
	 * the same primary key was already registered before, it will get removed
	 * and re-added. The following information will be added as well: Canonical
	 * SMILES (from RDKit), Fingerprint.
	 * 
	 * @param strPK
	 *            Primary key to be used for the molecule. Must not be null.
	 * @param sdf
	 *            An SDF record with the molecule and other information to be
	 *            added. Must not be null.
	 * @param listNames
	 *            Optional list of names to be added for synonym searches (e.g.
	 *            NVP number). Can be null.
	 * @param mapProperties
	 *            Optional list of properties to be added as other fields. Can be null.
	 */
	public void addMoleculeAsSDF(final String strPK, final SDFRecord sdf,
			final List<String> listNames, final Map<String, Object> mapProperties)
					throws IOException, GenericRDKitException {
		final String strStructure = sdf.getStructure();

		// final String strCanonSmiles = RDKFuncs.getCanonSmiles(strStructure, false);
		RWMol mol=RDKFuncs.MolBlockToMol(strStructure);
		final String strCanonSmiles = RDKFuncs.MolToSmiles(mol,true);

		if (mol != null && strCanonSmiles != null && !strCanonSmiles.isEmpty()) {
			addMolecule(mol, strPK, strCanonSmiles, listNames, mapProperties);
		}
		else {
			LOGGER.log(Level.WARNING, "Canonical SMILES could not be created for\n" + strStructure);
		}
		mol.delete();
	}

	/**
	 * Adds the specified molecule with the specified primary key and registers
	 * also the specified names (if not null) for the index. If a molecule with
	 * the same primary key was already registered before, it will get removed
	 * and re-added. The following information will be added as well: Canonical
	 * SMILES (from RDKit), Fingerprint.
	 * 
	 * @param strPK
	 *            Primary key to be used for the molecule. Must not be null.
	 * @param strSmiles
	 *            The SMILES of the molecule. Must not be null.
	 * @param listNames
	 *            Optional list of names to be added for synonym searches (e.g.
	 *            NVP number). Can be null.
	 * @param mapProperties
	 *            Optional list of properties to be added as other fields. Can be null.
	 */
	public void addMoleculeAsSmiles(final String strPK, final String strSmiles,
			final List<String> listNames, final Map<String, Object> mapProperties)
					throws IOException, GenericRDKitException {
		RWMol mol=RWMol.MolFromSmiles(strSmiles);
		final String strCanonSmiles = RDKFuncs.MolToSmiles(mol,true);
		mol.delete();

		addMolecule(mol, strPK, strCanonSmiles, listNames,
				mapProperties);
	}

	/**
	 * Returns the number of indexed molecules contained in this index.
	 * 
	 * @return Number of molecules. Or -1, if unknown.
	 */
	public int getIndexedMoleculeCount() throws IOException {
		try {
			final IndexSearcher searcher = prepareSearcher();
			if (searcher != null) {
				return searcher.getIndexReader().numDocs();
			}
			else {
				return -1;
			}
		}
		catch (final IOException exc) {
			return -1;
		}
	}

	/**
	 * Searches molecules based on a free text search, which may contain several
	 * fields.
	 * 
	 * @param strFreeSearch
	 *            Search string (human). Must not be null.
	 * @param iMaxHits
	 *            Maximum number of hits to return.
	 * 
	 * @return Collector with search results or null, if index has been
	 *         shutdown.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 * @throws ParseException
	 *             Thrown, if search string could not be parsed.
	 */
	public TopDocsCollector<ScoreDoc> searchMolecules(
			final String strFreeSearch, final int iMaxHits) throws IOException,
			ParseException {
		TopScoreDocCollector collector = null;

		final IndexSearcher searcher = prepareSearcher();
		if (searcher != null) {
			//final QueryParser queryParser = new QueryParser(LUCENE_VERSION,
			//		FIELD_NAME, m_analyzerFactory.createAnalyzer());

			final List<String> listFields = new ArrayList<String>(50);
			final FieldInfos fields = ReaderUtil.getMergedFieldInfos(searcher.getIndexReader());
			final Iterator<FieldInfo> fieldIterator = fields.iterator();
			while (fieldIterator.hasNext()) {
				listFields.add(fieldIterator.next().name);
			}
			final MultiFieldQueryParser mfQueryParser = new MultiFieldQueryParser(LUCENE_VERSION,
					listFields.toArray(new String[listFields.size()]), m_analyzerFactory.createAnalyzer());

			final Query query = mfQueryParser.parse(strFreeSearch);
			collector = TopScoreDocCollector.create(iMaxHits, true);
			searcher.search(query, collector);
		}

		return collector;
	}

	/**
	 * Searches molecules based on a name that has been registered in the name
	 * field.
	 * 
	 * @param strPK
	 *            Primary key of a molecule. Must not be null.
	 * 
	 * @return Document with the primary key or null, if not found.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 */
	public Document searchMoleculeByPK(final String strPK) throws IOException {
		Document doc = null;

		final IndexSearcher searcher = prepareSearcher();
		if (searcher != null) {
			final Query query = new TermQuery(new Term(FIELD_PK, strPK));
			final TopScoreDocCollector collector = TopScoreDocCollector.create(1, true);
			searcher.search(query, collector);
			if (collector.getTotalHits() > 0) {
				doc = searcher.doc(collector.topDocs().scoreDocs[0].doc);
			}
		}

		return doc;
	}

	/**
	 * Searches molecules based on a name that has been registered in the name
	 * field.
	 * 
	 * @param strName
	 *            Name of a molecule. Must not be null.
	 * @param iMaxHits
	 *            Maximum number of hits to return.
	 * 
	 * @return Collector with search results or null, if index has been
	 *         shutdown.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 * @throws ParseException
	 *             Thrown, if search string could not be parsed.
	 */
	public TopDocsCollector<ScoreDoc> searchMoleculesByName(
			final String strName, final int iMaxHits) throws IOException {
		TopScoreDocCollector collector = null;

		final IndexSearcher searcher = prepareSearcher();
		if (searcher != null) {
			final Query query1 = new TermQuery(new Term(FIELD_NAME, strName));
			final Query query2 = new TermQuery(new Term(FIELD_PK, strName));
			final BooleanQuery query = new BooleanQuery();
			query.add(query1, BooleanClause.Occur.SHOULD);
			query.add(query2, BooleanClause.Occur.SHOULD);
			collector = TopScoreDocCollector.create(iMaxHits, true);
			searcher.search(query, collector);
		}

		return collector;
	}

	/**
	 * Searches molecules based on a canonical smiles. The passed in smile will
	 * be canonicalized and compared to known molecule data.
	 * 
	 * @param strSmiles
	 *            Smiles to search for. Must not be null. Does not need to be in
	 *            canonical form yet.
	 * @param iMaxHits
	 *            Maximum number of hits to return.
	 * 
	 * @return Collector with search results or null, if index has been
	 *         shutdown.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 * @throws ParseException
	 *             Thrown, if search string could not be parsed.
	 */
	public TopDocsCollector<ScoreDoc> searchExactMolecules(
			final String strSmiles, final int iMaxHits) throws IOException,
			GenericRDKitException {
		TopScoreDocCollector collector = null;

		final IndexSearcher searcher = prepareSearcher();
		if (searcher != null) {
			// Convert SMILES into RDKit Molecule and canonicalize
			final String canonSmiles = RDKFuncs.getCanonSmiles(strSmiles, true);
			final Query query = new TermQuery(new Term(FIELD_SMILES, canonSmiles));
			collector = TopScoreDocCollector.create(iMaxHits, true);
			searcher.search(query, collector);
		}

		return collector;
	}

	/**
	 * Searches similar molecules based on fingerprint matches.
	 * 
	 * @param strSmiles
	 *            Smiles to search for. Must not be null.
	 * @param iMaxHits
	 *            Maximum number of hits to return.
	 * 
	 * @return Collector with search results or null, if index has been
	 *         shutdown.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 * @throws ParseException
	 *             Thrown, if search string could not be parsed.
	 */
	public TopDocsCollector<ScoreDoc> searchMoleculesByFingerprintMatch(
			final String strSmiles, final int iMaxHits) throws IOException {
		if (strSmiles == null) {
			throw new IllegalArgumentException("SMILES must not be null.");
		}

		TopScoreDocCollector collector = null;

		final IndexSearcher searcher = prepareSearcher();
		if (searcher != null) {
			// Calculate query fingerprint
			final BitSet fpQuery = m_fingerprintFactory.createQueryFingerprint(strSmiles, false);

			if (fpQuery != null) {
				// Create query for checking if all query fingerprint bit positions
				// are matching set bits in a molecules fingerprint
				final BooleanQuery query = new BooleanQuery();
				for (int i = fpQuery.nextSetBit(0); i >= 0; i = fpQuery
						.nextSetBit(i + 1)) {
					query.add(new BooleanClause(new TermQuery(new Term(FIELD_FP,
							Integer.toString(i))), BooleanClause.Occur.MUST));
				}

				// Perform the search
				collector = TopScoreDocCollector.create(iMaxHits, true);
				searcher.search(query, collector);
			}
		}

		return collector;
	}

	/**
	 * Searches molecules which contain the passed in molecule as a
	 * substructure. This is based on fingerprint matches as well as
	 * substructure searches.
	 * 
	 * @param strSmiles
	 *            Smiles to search for. Must not be null.
	 * @param iMaxHits
	 *            Maximum number of hits to return.
	 * 
	 * @return Collector with search results or null, if index has been
	 *         shutdown.
	 * 
	 * @throws IOException
	 *             Thrown, if index could not be read.
	 * @throws ParseException
	 *             Thrown, if search string could not be parsed.
	 */
	public TopDocsCollector<ScoreDoc> searchMoleculesWithSubstructure(
			final String strSmiles, final int iMaxHits) throws IOException {
		SubstructureScoreDocCollector collector = null;
		final TopDocsCollector<ScoreDoc> colFpMatch = searchMoleculesByFingerprintMatch(
				strSmiles, Math.min(iMaxHits * 10, 100000));
		int iErrors = 0;

		IndexSearcher searcher = null;
		if (colFpMatch != null && (searcher = prepareSearcher()) != null) {
			// Scored in order, because colFpMatch is scored in order already
			collector = SubstructureScoreDocCollector.create(iMaxHits, true);

			// If similar molecules have been found, walk through them and check
			// for substructures
			if (colFpMatch.getTotalHits() > 0) {
				final int iWaveId = RDKit.createUniqueCleanupWaveId();
				try {
					final RWMol molQuery = RDKit.markForCleanup(RWMol.MolFromSmiles(strSmiles, 0, false), iWaveId);

					if (molQuery != null) {
						final TopDocs topDocs = colFpMatch.topDocs();
						if (topDocs != null) {
							final ScoreDoc[] arrScoreDoc = topDocs.scoreDocs;
							int iCountHits = 0;
							if (arrScoreDoc != null) {
								final int iLength = arrScoreDoc.length;
								for (int i = 0; i < iLength && iCountHits < iMaxHits; i++) {
									final int iDocID = arrScoreDoc[i].doc;
									final Document doc = searcher.doc(iDocID);
									if (doc != null) {
										final String smilesExisting = doc.get(FIELD_SMILES);
										if (smilesExisting != null) {
											final int iWaveIdLoop = RDKit
													.createUniqueCleanupWaveId();
											try {
												final RWMol mol = RDKit.markForCleanup(RWMol
														.MolFromSmiles(smilesExisting, 0, false), iWaveIdLoop);
												mol.updatePropertyCache(false);
												if (mol.hasSubstructMatch(molQuery)) {
													iCountHits++;
													collector.collect(iDocID, arrScoreDoc[i].score);
												}
											}
											catch (final GenericRDKitException exc) {
												iErrors++;
											}
											finally {
												RDKit.cleanupMarkedObjects(iWaveIdLoop);
											}
										}
									}
								}
							}
						}
					}
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search SMILES could not be used.", exc);
				}
				finally {
					RDKit.cleanupMarkedObjects(iWaveId);
				}
			}

			if (iErrors > 0) {
				LOGGER.log(Level.SEVERE, iErrors + " molecules failed substructure searching.");
			}
		}

		return collector;
	}

	/**
	 * A convenience method to get the primary keys of the documents, which have
	 * been found by a search and are now contained in a Collector object.
	 * 
	 * @param collector
	 *            Search result. Can be null.
	 * 
	 * @return Array of primary keys in the order that the collector provides.
	 *         Can be empty, but will never be null.
	 */
	public String[] getPrimaryKeysForSearchHits(final TopDocsCollector<ScoreDoc> collector)
			throws IOException {
		String[] arrRet = EMPTY_RESULTS;

		IndexSearcher searcher = null;
		if (collector != null && (searcher = prepareSearcher()) != null) {
			final TopDocs topDocs = collector.topDocs();
			if (topDocs != null) {
				final List<String> listPKs = new ArrayList<String>(topDocs.totalHits);
				final ScoreDoc[] arrScoreDoc = topDocs.scoreDocs;
				if (arrScoreDoc != null) {
					final int iLength = arrScoreDoc.length;
					arrRet = new String[iLength];
					for (int i = 0; i < iLength; i++) {
						final Document doc = searcher.doc(arrScoreDoc[i].doc);
						if (doc != null) {
							final Fieldable fieldPk = doc.getFieldable(FIELD_PK);
							if (fieldPk != null) {
								listPKs.add(fieldPk.stringValue());
							}
						}
					}
				}
				arrRet = listPKs.toArray(new String[listPKs.size()]);
			}
		}

		return arrRet;
	}

	//
	// Protected Methods
	//

	/**
	 * Adds the RDKit molecule with the specified primary key to the index.
	 * 
	 * @param strPK
	 *            Primary key to be used for the molecule. Must not be null.
	 * @param canonSmiles
	 *            Canonical Smiles. Must not be null.
	 * @param listNames
	 *            Optional list of names to be added for synonym searches (e.g.
	 *            NVP number). Can be null.
	 * @param mapProperties
	 *            Optional list of properties to be added as other fields. Can be null.
	 */
	protected void addMolecule(final ROMol mol, final String strPK, final String canonSmiles,
			final List<String> listNames, final Map<String, Object> mapProperties)
					throws IOException, GenericRDKitException {
		// Pre-checks
		if (strPK == null) {
			throw new IllegalArgumentException("Primary key must not be null.");
		}
		if (canonSmiles == null || canonSmiles.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Canonical SMILES must not be null or empty.");
		}

		final IndexWriter writer = prepareWriter();
		if (writer != null) {
			// Delete existing index document with the same PK (primary key)
			writer.deleteDocuments(new TermQuery(new Term(FIELD_PK, strPK)));

			// OR:
			// Delete existing index document with the same canonical smiles
			// if (canonSmiles != null) {
			// writer.deleteDocuments(new TermQuery(new Term(FIELD_CANON_SMILES,
			// canonSmiles)));
			// }

			final BitSet fp = m_fingerprintFactory.createStructureFingerprint(mol);

			// Create new index document
			final Document doc = new Document();
			doc.add(new Field(FIELD_PK, strPK, Store.YES,
					Index.NOT_ANALYZED_NO_NORMS));

			// This is the canonical SMILES structure
			doc.add(new Field(FIELD_SMILES, canonSmiles, Store.YES,
					Index.NOT_ANALYZED_NO_NORMS));

			// For the fingerprint we store only the bit positions as numbers
			for (int i = fp.nextSetBit(0); i >= 0; i = fp.nextSetBit(i + 1)) {
				doc.add(new Field(FIELD_FP, Integer.toString(i), Store.NO,
						Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.NO));
			}

			// Add names for the molecule
			if (listNames != null) {
				for (final String name : listNames) {
					doc.add(new Field(FIELD_NAME, name, Store.YES,
							Index.NOT_ANALYZED_NO_NORMS));
				}
			}

			// Add other properties for the molecule
			if (mapProperties != null) {
				for (final String key : mapProperties.keySet()) {
					final Object value = mapProperties.get(key);
					if (value != null) {
						final String strValue = value.toString();
						doc.add(new Field(key, strValue, Store.YES,
								Index.NOT_ANALYZED_NO_NORMS));
					}
				}
			}

			writer.addDocument(doc);

			onMoleculeAdded(strPK, canonSmiles);
		}
		else {
			throw new IOException("Index writer is unavailable.");
		}
	}

	/**
	 * Creates the index writer, if it is currently closed.
	 * 
	 * @return The writer, if one is available. Null otherwise.
	 * 
	 * @throws IOException
	 *             Thrown, if index writer could not be opened.
	 */
	protected IndexWriter prepareWriter() throws IOException {
		if (isShutdown()) {
			return null;
		}

		if (m_writer == null) {
			close(); // Close a reader
			m_writer = new IndexWriter(m_directory,
					m_configFactory.createIndexWriterConfig(m_analyzerFactory.createAnalyzer()));
		}

		return m_writer;
	}

	/**
	 * Creates the index searcher, if it is currently closed.
	 * 
	 * @return The searcher, if one is available. Null otherwise.
	 * 
	 * @throws IOException
	 *             Thrown, if index searcher could not be opened.
	 */
	protected IndexSearcher prepareSearcher() throws IOException {
		if (isShutdown()) {
			return null;
		}

		if (m_searcher == null) {
			close(); // Close a writer
			try {
				m_searcher = new IndexSearcher(IndexReader.open(m_directory));
			}
			catch (final IndexNotFoundException exc) {
				LOGGER.log(Level.WARNING, "The index does not exist yet.");
				throw new IOException("The index does not exist yet.", exc);
			}
			// m_searcher.setSimilarity(new ChemicalSimiliarity()); // TODO
		}

		return m_searcher;
	}

	//
	// Static Public Methods
	//

	/**
	 * Convenience method to ensure that the specified directory exists and can
	 * be written to.
	 * 
	 * @param directory
	 *            Directory to test / to create. Must not be null.
	 * @param bThrowException
	 *            Set to true to throw an IllegalArgumentException, if directory
	 *            creation / accessed is failing.
	 * 
	 * @return True, if directory is ready to be used. False otherwise (if no
	 *         exception shall be thrown).
	 */
	public static boolean prepareIndexDirectory(final File directory,
			final boolean bThrowException) {
		// Pre-check
		if (directory == null) {
			throw new IllegalArgumentException(
					"Index directory must not be null.");
		}

		boolean bSuccess = true;

		// Deep check
		if (directory.exists()) {
			if (!directory.isDirectory()) {
				if (bThrowException) {
					throw new IllegalArgumentException("'"
							+ directory.getAbsolutePath()
							+ "' is not a directory.");
				}
				else {
					bSuccess = false;
				}
			}
			else if (!directory.canWrite()) {
				if (bThrowException) {
					throw new IllegalArgumentException(
							"Cannot write into index directory '"
									+ directory.getAbsolutePath() + "'.");
				}
				else {
					bSuccess = false;
				}
			}
		}
		else {
			if (!directory.mkdirs()) {
				if (bThrowException) {
					throw new IllegalArgumentException(
							"Unable to create index directory '"
									+ directory.getAbsolutePath() + "'.");
				}
				else {
					bSuccess = false;
				}
			}
		}

		return bSuccess;
	}

	protected String createCanonicalizedSmilesFromSdf(final String strSdf) {
		String strSmiles = null;

		if (strSdf != null && !strSdf.trim().isEmpty()) {
			ROMol mol = null;

			try {
				Exception excCaught = null;

				// As first step try to parse the input molecule format
				try {
					mol = RWMol.MolFromMolBlock(strSdf);
				}
				catch (final Exception exc) {
					// Parsing failed and RDKit molecule is null
					excCaught = exc;
				}

				// If we got an RDKit molecule, parsing was successful, now create the SMILES from it and the cell
				if (mol != null) {
					try {
						if (mol.getNumAtoms() > 0) {
							strSmiles = RDKFuncs.MolToSmiles(mol, true);
						}
						else {
							strSmiles = "";
						}
					}
					catch (final Exception exc) {
						excCaught = exc;
					}
				}

				// Do error handling depending on user settings
				if (mol == null || excCaught != null) {
					// Find error message
					final StringBuilder sbError = new StringBuilder("SDF");

					// Specify error type
					if (mol == null) {
						sbError.append(" Parsing Error (");
					}
					else {
						sbError.append(" Process Error (");
					}

					// Specify exception
					if (excCaught != null) {
						sbError.append(excCaught.getClass().getSimpleName());

						// Specify error message
						final String strMessage = excCaught.getMessage();
						if (strMessage != null) {
							sbError.append(" (").append(strMessage).append(")");
						}
					}
					else {
						sbError.append("Details unknown");
					}

					sbError.append(") for\n" + strSdf);

					// Report the error
					LOGGER.severe(sbError.toString());
				}
			}
			finally {
				if (mol != null) {
					mol.delete();
				}
			}
		}

		return strSmiles;
	}
}
