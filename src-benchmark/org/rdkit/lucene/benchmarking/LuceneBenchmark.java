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
package org.rdkit.lucene.benchmarking;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.RDKit.RDKFuncs;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.store.NIOFSDirectory;
import org.rdkit.lucene.ChemicalIndex;
import org.rdkit.lucene.IndexListener;
import org.rdkit.lucene.StandardAnalyzerFactory;
import org.rdkit.lucene.fingerprint.DefaultFingerprintFactory;
import org.rdkit.lucene.fingerprint.DefaultFingerprintSettings;
import org.rdkit.lucene.fingerprint.FingerprintType;

/**
 * A Lucene benchmark based on a ChemicalIndex and a set of SMILES
 * that are used for Molecule search, Fingerprint Match search and Substructure
 * Search. Output is given in CSV format to the specified file.
 * 
 * As the startup of the index takes some time and leads to long searches,
 * the first search will be not counted. Searches are performed
 * in random order on the set of SMILES in focus. For every SMILE and search type
 * we will perform three searches in total and calculate also the average time.
 * 
 * The benchmark records the following columns in the CSV - rows are in order
 * of the passed in SMILES:
 * SMILES,
 * Molecule Search: Hits, Time1, Time2, Time3, AvgTime,
 * Fingerprint Match Search: Hits, Time1, Time2, Time3, AvgTime
 * Substructure Search: Hits, Time1, Time2, Time3, AvgTime
 * 
 * @author Manuel Schwarze
 */
public class LuceneBenchmark {

	//
	// Enum
	//

	public enum SearchType {
		MOL, FP, SS
	}

	//
	// Inner Classes
	//

	static class SearchBenchmarkItem {

		//
		// Members
		//

		private final String m_strSmiles;
		private final int m_iRowNumber;
		private final SearchType m_searchType;
		private final int m_iSearchCount;
		private final List<Integer> m_listHits = new ArrayList<Integer>();
		private final List<Integer> m_listTimeInMs = new ArrayList<Integer>();
		private final List<Integer> m_listSearchNumber = new ArrayList<Integer>();
		private long m_lStartTs;

		//
		// Constructor
		//

		public SearchBenchmarkItem(final String strSmiles, final int iRowNumber, final SearchType searchType, final int iSearchCount) {
			m_strSmiles = strSmiles;
			m_iRowNumber = iRowNumber;
			m_searchType = searchType;
			m_iSearchCount = iSearchCount;
		}

		//
		// Public Methods
		//

		public void searchStarted(final int iSearchNumber) {
			m_listSearchNumber.add(iSearchNumber);
			m_lStartTs = System.currentTimeMillis();
		}

		public void searchFailed() {
			m_listHits.add(Integer.MAX_VALUE);
			m_listTimeInMs.add((int)(System.currentTimeMillis() - m_lStartTs));
		}

		public void searchFinished(final int iHits) {
			m_listHits.add(iHits);
			m_listTimeInMs.add((int)(System.currentTimeMillis() - m_lStartTs));
		}

		public boolean isDone() {
			return m_listHits.size() == m_iSearchCount;
		}

		public String getSmiles() {
			return m_strSmiles;
		}

		public SearchType getSearchType() {
			return m_searchType;
		}

		public int getAverageHits() {
			long lSum = 0;
			final int iCount = m_listHits.size();

			for (int i = 0; i < iCount; i++) {
				lSum += m_listHits.get(i);
			}

			return (int)(iCount > 0 ? (lSum / iCount) : -1);
		}

		public List<Integer> getHits() {
			return m_listHits;
		}

		public int getAverageTimeInMs() {
			long lSum = 0;
			final int iCount = m_listTimeInMs.size();

			for (int i = 0; i < iCount; i++) {
				lSum += m_listTimeInMs.get(i);
			}

			return (int)(iCount > 0 ? (lSum / iCount) : -1);
		}

		public List<Integer> getTimeInMs() {
			return m_listTimeInMs;
		}

		public int getRowNumber() {
			return m_iRowNumber;
		}

		public List<Integer> getSearchNumbers() {
			return m_listSearchNumber;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("SearchBenchmarkItem { ");
			sb.append("smiles=").append(getSmiles()).append(", ");
			sb.append("rowNumber=").append(getRowNumber()).append(", ");
			sb.append("searchType=").append(getSearchType()).append(", ");
			sb.append("searchNumbers=").append(getSearchNumbers()).append(", ");
			sb.append("hits=").append(getHits()).append(", ");
			sb.append("avgHits=").append(getAverageHits()).append(", ");
			sb.append("times=").append(getTimeInMs()).append(", ");
			sb.append("avgTimes=").append(getAverageTimeInMs());
			sb.append(" }");

			return sb.toString();
		}
	}

	//
	// Constants
	//

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(LuceneBenchmark.class.getName());

	//
	// Members
	//

	private final ChemicalIndex m_chemIndex;

	private final List<SearchBenchmarkItem> m_listAvailableSearchItems;

	private final List<SearchBenchmarkItem> m_listInProgressItems;

	private final List<SearchBenchmarkItem> m_listFinishedSearchItems;

	private final Map<Integer, Map<SearchType, SearchBenchmarkItem>> m_mapRowIdToMapSearchTypeToSearchItems;

	private final String m_strOutputFileCsv;

	private final List<SearchType> m_listSearchTypes;

	private final int m_iSearchCount;

	private final int m_iThreadCount;

	private boolean m_bHeaderWritten;

	private AtomicInteger m_aiSearchNumber;

	//
	// Constructor
	//

	/**
	 * A Lucene benchmark based on the passed in ChemicalIndex and a set of
	 * SMILES that are used for Molecule search, Fingerprint Match search and
	 * Substructure Search. Output is given in CSV format to the specified file.
	 * If the file exists already an increasing number will be appended.
	 * The search count defines how often we run a search to take the average time at the end.
	 */
	public LuceneBenchmark(final ChemicalIndex chemIndex,
			final String strInputFileWithSmiles, final int iStartLine, final int iEndLine,
			final String strOutputFileCsv, final int iSearchCount,
			final int iThreadCount, final SearchType... searchTypes) throws IOException {
		m_chemIndex = chemIndex;
		m_listAvailableSearchItems = new ArrayList<SearchBenchmarkItem>();
		m_listInProgressItems = new ArrayList<SearchBenchmarkItem>();
		m_listFinishedSearchItems = new ArrayList<SearchBenchmarkItem>();
		m_mapRowIdToMapSearchTypeToSearchItems = new HashMap<Integer, Map<SearchType, SearchBenchmarkItem>>();
		m_strOutputFileCsv = strOutputFileCsv;
		m_listSearchTypes = Arrays.asList(searchTypes);
		m_iSearchCount = iSearchCount;
		m_iThreadCount = iThreadCount;
		m_bHeaderWritten = false;
		final FileReader inFile = new FileReader(strInputFileWithSmiles);
		final LineNumberReader lineReader = new LineNumberReader(inFile);
		String strLine;
		while ((strLine = lineReader.readLine()) != null) {
			final int iLineNumber = lineReader.getLineNumber();
			if (iStartLine == -1 || iLineNumber >= iStartLine) {
				if (iEndLine == -1 || iLineNumber <= iEndLine) {
					strLine = strLine.replaceAll("\t", " ");
					final int indexSmilesEnd = strLine.indexOf(" ");
					if (indexSmilesEnd > -1) {
						strLine = strLine.substring(0, indexSmilesEnd);
					}
					for (final SearchType searchType : m_listSearchTypes) {
						m_listAvailableSearchItems.add(new SearchBenchmarkItem(strLine, iLineNumber, searchType, iSearchCount));
					}
				}
			}
		}
	}

	public int getOverAllSearches() {
		return m_aiSearchNumber.get();
	}


	public int run() {
		m_aiSearchNumber = new AtomicInteger();
		final ExecutorService exec = Executors.newFixedThreadPool(m_iThreadCount);
		final long lStart = System.currentTimeMillis();

		for (;;) {
			final SearchBenchmarkItem item = pickNextSearchItem();
			if (item == null) {
				if (areAllItemsProcessed()) {
					break;
				}
				else {
					try {
						Thread.sleep(1000); // rest for a second
					}
					catch (final InterruptedException e) {
						LOGGER.info("Running the tests was interrupted.");
						break;
					}
				}
			}
			else {
				final int iSearchNumber = m_aiSearchNumber.incrementAndGet();

				try {
					exec.execute(new Runnable() {
						@Override
						public void run() {
							runTest(item, iSearchNumber);
						}
					});
				}
				catch (final RejectedExecutionException exc) {
					LOGGER.log(Level.SEVERE, "Search could not be run. No threads available.", exc);
				}
			}
		}

		exec.shutdown();

		try {
			exec.awaitTermination(10, TimeUnit.MINUTES);
		}
		catch (final InterruptedException exc) {
			// Ignore, just exit
		}

		final long lEnd = System.currentTimeMillis();

		return (int)(lEnd - lStart);
	}

	//
	// Protected Methods
	//

	protected void runTest(final SearchBenchmarkItem item, final int iSearchNumber) {
		try {
			TopDocsCollector<ScoreDoc> collector = null;
			item.searchStarted(iSearchNumber);
			switch (item.getSearchType()) {
			case MOL:
				collector = m_chemIndex.searchExactMolecules(item.getSmiles(), 1000000);
				break;
			case FP:
				collector = m_chemIndex.searchMoleculesByFingerprintMatch(item.getSmiles(), 1000000);
				break;
			case SS:
				collector = m_chemIndex.searchMoleculesWithSubstructure(item.getSmiles(), 50000);
				break;
			}

			if (collector == null) {
				item.searchFailed();
			}
			else {
				item.searchFinished(collector.getTotalHits());
			}
		}
		catch (final Exception exc) {
			item.searchFailed();
		}

		putBackSearchItem(item);

		LOGGER.info(item.toString());
	}

	protected boolean areAllItemsProcessed() {
		synchronized (m_listAvailableSearchItems) {
			return m_listInProgressItems.isEmpty() && m_listAvailableSearchItems.isEmpty();
		}
	}

	protected SearchBenchmarkItem pickNextSearchItem() {
		synchronized (m_listAvailableSearchItems) {
			SearchBenchmarkItem next = null;

			if (!m_listAvailableSearchItems.isEmpty()) {
				next = m_listAvailableSearchItems.remove((int)(Math.random() * m_listAvailableSearchItems.size()));
				m_listInProgressItems.add(next);
			}

			return next;
		}
	}

	protected void putBackSearchItem(final SearchBenchmarkItem item) {
		synchronized (m_listAvailableSearchItems) {
			m_listInProgressItems.remove(item);
			if (item.isDone()) {
				m_listFinishedSearchItems.add(item);
				final int iRowId = item.getRowNumber();
				Map<SearchType, SearchBenchmarkItem> mapSearchItems =
						m_mapRowIdToMapSearchTypeToSearchItems.get(iRowId);
				if (mapSearchItems == null) {
					mapSearchItems = new HashMap<SearchType, SearchBenchmarkItem>();
					m_mapRowIdToMapSearchTypeToSearchItems.put(iRowId, mapSearchItems);
				}
				mapSearchItems.put(item.getSearchType(), item);
				if (mapSearchItems.size() == m_listSearchTypes.size()) {
					try {
						if (!m_bHeaderWritten) {
							writeResultFileHeader();
							m_bHeaderWritten = true;
						}
						writeResultFileRow(mapSearchItems);
					}
					catch (final IOException exc) {

					}
				}
			}
			else {
				m_listAvailableSearchItems.add(item);
			}
		}
	}

	protected void writeResultFileHeader() throws IOException {
		synchronized (m_strOutputFileCsv) {
			final FileOutputStream out = new FileOutputStream(new File(m_strOutputFileCsv), true);
			final StringBuilder sbHeader = new StringBuilder();
			sbHeader.append("Row");
			sbHeader.append(";Smiles");
			for (final SearchType searchType : m_listSearchTypes) {
				final String strPrefix = searchType.name() + "-";

				if (m_iSearchCount < 2) {
					sbHeader.append(';').append("Search Number");
					sbHeader.append(';').append(strPrefix).append("Hits");
					sbHeader.append(';').append(strPrefix).append("Time (in ms)");
				}
				else {
					sbHeader.append(';').append(strPrefix).append("AvgHits");
					sbHeader.append(';').append(strPrefix).append("AvgTime (in ms)");
					for (int i = 1; i <= m_iSearchCount; i++) {
						sbHeader.append(';').append(strPrefix).append("Search#[").append(i).append("]");
						sbHeader.append(';').append(strPrefix).append("Hits[").append(i).append("]");
						sbHeader.append(';').append(strPrefix).append("Time[").append(i).append("] (in ms)");
					}
				}
			}
			sbHeader.append(';').append("Concurrent Threads");
			sbHeader.append("\r\n");

			out.write(sbHeader.toString().getBytes());
			out.close();
		}
	}

	protected void writeResultFileRow(final Map<SearchType, SearchBenchmarkItem> hItems) throws IOException {
		synchronized (m_strOutputFileCsv) {
			final FileOutputStream out = new FileOutputStream(new File(m_strOutputFileCsv), true);
			final StringBuilder sbRow = new StringBuilder();
			boolean bRowHeaderWritten = false;
			for (final SearchType searchType : m_listSearchTypes) {
				final SearchBenchmarkItem item = hItems.get(searchType);
				if (item != null) {
					if (!bRowHeaderWritten) {
						sbRow.append(item.getRowNumber());
						sbRow.append(";").append(item.getSmiles());
						bRowHeaderWritten = true;
					}
					if (m_iSearchCount < 2) {
						sbRow.append(';').append(item.getSearchNumbers().get(0));
					}
					sbRow.append(';').append(item.getAverageHits());
					sbRow.append(';').append(item.getAverageTimeInMs());
					if (m_iSearchCount > 1) {
						for (int i = 0; i < m_iSearchCount; i++) {
							sbRow.append(';').append(item.getSearchNumbers().get(i));
							sbRow.append(';').append(item.getHits().get(i));
							sbRow.append(';').append(item.getTimeInMs().get(i));
						}
					}
				}
			}
			sbRow.append(';').append(m_iThreadCount);
			sbRow.append("\r\n");

			out.write(sbRow.toString().getBytes());
			out.close();
		}
	}

	//
	// Static Methods
	//

	public static String prepareBenchmarkCsvFile(final String strFileName) {
		File fileRet = null;
		final File outFile = new File(strFileName);
		final File outDirectory = outFile.getParentFile();

		// Create directory, if necessary
		if (outDirectory != null) {
			if (outDirectory.exists() && !outDirectory.isDirectory()) {
				throw new IllegalArgumentException("Output directory is a file.");
			}
			if (!outDirectory.exists() && !outDirectory.mkdirs()) {
				throw new IllegalArgumentException("Output directory does not exist and could not be created.");
			}
		}

		if (outFile.exists()) {
			int iNumber = 1;
			while ((fileRet = new File(outFile.getParentFile(),
					insertNumberInFilename(outFile.getName(), iNumber++))).exists()) {
			}
		}
		else {
			fileRet = outFile;
		}

		return fileRet.getAbsolutePath();
	}

	private static String insertNumberInFilename(final String strFilename, final int iNumber) {
		String strRet = null;

		if (strFilename != null) {
			final int indexExt = strFilename.lastIndexOf(".");
			if (indexExt > -1) {
				strRet = strFilename.substring(0, indexExt) + "_" + iNumber +
						strFilename.substring(indexExt);
			}
			else {
				strRet = strFilename + "_" + iNumber;
			}
		}

		return strRet;
	}

	public static void printInfoAndExit() {
		System.out.println("LuceneBenchmark usage:\n" +
				"    LuceneBenchmark -index <indexDirectory> <sdfFile> <sdfFieldForPrimaryKey> [<listOfPrimaryKeysToIgnore>...]\n" +
				" or LuceneBenchmark -benchmark <indexDirecory> <benchmarkIniFile> [<benchmarkIniFile>...]\n" +
				"\n" +
				"Config file must be in the properties format of Java. The following keys are known:\n" +
				"querySmilesFile: The input query file with SMILES. Mandatory.\n" +
				"firstRow: The first row to be used for benchmarking. Optional. Default is -, which means beginning of file.\n" +
				"lastRow: The last row to be used for benchmarking. Optional. Default is -1, which means end of file.\n" +
				"searchTypes: Comma-separated search types. Optional. Default is FP,SS,MOL\n" +
				"             FP = Fingerprint, SS = Substructure, MOL = Exact Molecule Search\n" +
				"searchesPerType: Number of searches to be performed per row and per search type. Optional. Default is 1.\n" +
				"threadCount: Number of concurrent threads to be used. One thread = one query. Optional. Default is 1.\n" +
				"resultCsvFile: The output result file in CSV format. Optional. Default is\n" +
				"             %querySmilesFile% (%threadCount% threads, %searches% searches, %searchTypes% in %time% min).csv\n" +
				"             The following placeholders are allowed: \n" +
				"                 %querySmilesFile%, %firstRow%, %lastRow%, \n" +
				"                 %searchTypes%, %searchesPerType%, %threadCount%, \n" +
				"                 %time% (which is the overall search time, \n" +
				"                 %searchCount% (which is the overall search count");
		System.exit(1);
	}

	public static void index(final String strIndexDirectory, final String strSdfFile, final String strFieldPK, final String... arrPKsToIgnore) throws IOException {
		ChemicalIndex.prepareIndexDirectory(new File(strIndexDirectory), true);
		HashSet<String> setPKsToIgnore = null;

		if (arrPKsToIgnore != null) {
			setPKsToIgnore = new HashSet<String>(arrPKsToIgnore.length);
			for (final String strPK : arrPKsToIgnore) {
				setPKsToIgnore.add(strPK);
			}
		}

		final ChemicalIndex chemIndex = new ChemicalIndex(new NIOFSDirectory(new File(strIndexDirectory)),
				new StandardAnalyzerFactory(),

				// Define here how fingerprints shall be used
				new DefaultFingerprintFactory(
						// Define structure fingerprint settings (used when indexing molecules)
						new DefaultFingerprintSettings(FingerprintType.avalon)
						.setNumBits(512)
						.setAvalonQueryFlag(0)
						.setAvalonBitFlags(RDKFuncs.getAvalonSSSBits()),
						// Define query fingerprint settings (used when searching molecules)
						new DefaultFingerprintSettings(FingerprintType.avalon)
						.setNumBits(512)
						.setAvalonQueryFlag(1)
						.setAvalonBitFlags(RDKFuncs.getAvalonSSSBits())),

						null);

		chemIndex.addIndexListener(new IndexListener() {
			private final AtomicInteger m_iAddedMoleculeCount = new AtomicInteger(0);
			private final StringBuilder m_sb = new StringBuilder(200);

			@Override
			public void onMoleculeAdded(final String strPK, final String strSmiles) {
				final int iCount = m_iAddedMoleculeCount.incrementAndGet();
				if (iCount % 500 == 0) {
					synchronized (m_sb) {
						m_sb.setLength(0);
						m_sb.append("Added ").append(iCount).
						append(" molecules (last one: ").append(strPK).append(")");
						System.out.println(m_sb.toString());
					}
				}
			}
		});

		chemIndex.addSDFFileToIndex(new File(strSdfFile), strFieldPK, null, setPKsToIgnore);
		chemIndex.shutdown();
	}

	public static void benchmark(final String strIndexDirectory, final String... arrConfigFiles) throws IOException {
		System.out.println("LuceneBenchmark - benchmark");
		System.out.println("Index Directory: " + strIndexDirectory);

		// Read all property files
		final Properties[] arrPropsBenchmarking = new Properties[arrConfigFiles.length];
		for (int i = 0; i < arrConfigFiles.length; i++) {
			System.out.println("Config File: " + arrConfigFiles[i]);
			arrPropsBenchmarking[i] = readConfigFile(arrConfigFiles[i]);
		}

		// Run the benchmarks one after the other
		// Setup parameters
		for (int i = 0; i < arrConfigFiles.length; i++) {
			ChemicalIndex.prepareIndexDirectory(new File(strIndexDirectory), true);
			final ChemicalIndex chemIndex = new ChemicalIndex(new NIOFSDirectory(new File(strIndexDirectory)),
					new StandardAnalyzerFactory(),
					new DefaultFingerprintFactory(new DefaultFingerprintSettings(FingerprintType.avalon).setNumBits(512)),
					null);

			// Some warm-up queries to initialize caches (does not count for benchmark)
			System.out.println("Warming up system ...");
			final String[] arrWarmupSmiles = new String[] {
					"[H]CCCN1C=C2C(=C(c3occc3)C(=O)C([H])(C)C2=O)C=C1CCCC",
					"CS(=O)C",
					"C1CC2[C@@H]1C[NH2+]2",
					"[H]c1ccc(OCC)cc1",
					"[H]CCCC([H])C(=O)[O-]"
			};
			for (final String strSmiles : arrWarmupSmiles) {
				final TopDocsCollector<ScoreDoc> collector =
						chemIndex.searchMoleculesByFingerprintMatch(strSmiles, 1000000);
				try {
					chemIndex.getPrimaryKeysForSearchHits(collector);
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Unable to deliver search results.", exc);
				}
			}
			System.out.println("Warm up of system is done. Starting benchmarking ...");

			final String strInputFileWithSmiles = new File(arrPropsBenchmarking[i].getProperty("querySmilesFile").toString()).getAbsolutePath();
			final int iStartLine = Integer.parseInt(arrPropsBenchmarking[i].getProperty("firstRow").toString());
			final int iEndLine = Integer.parseInt(arrPropsBenchmarking[i].getProperty("lastRow").toString());

			final String strSearchTypes = arrPropsBenchmarking[i].getProperty("searchTypes").toString();
			final List<SearchType> listSearchTypes = new ArrayList<SearchType>();
			for (final SearchType searchType : SearchType.values()) {
				if (strSearchTypes.contains(searchType.name())) {
					listSearchTypes.add(searchType);
				}
			}
			final SearchType[] arrSearchTypes = listSearchTypes.toArray(new SearchType[listSearchTypes.size()]);

			final int iSearchCount = Integer.parseInt(arrPropsBenchmarking[i].getProperty("searchesPerType").toString());
			final int iThreadCount = Integer.parseInt(arrPropsBenchmarking[i].getProperty("threadCount").toString());

			final String strOutputFileCsv = prepareBenchmarkCsvFile(arrPropsBenchmarking[i].getProperty("resultCsvFile").toString());

			System.out.println("Configuration taken from  " + arrPropsBenchmarking[i]);
			System.out.println("Input will be taken from " + strInputFileWithSmiles + " (lines " + iStartLine + "-" + iEndLine + ")");
			System.out.println("Output will be written to " + strOutputFileCsv);
			System.out.println("Configuration details: \n" + arrPropsBenchmarking[i]);

			// Setup benchmark
			final LuceneBenchmark benchmark = new LuceneBenchmark(
					chemIndex, strInputFileWithSmiles, iStartLine, iEndLine,
					strOutputFileCsv, iSearchCount, iThreadCount, arrSearchTypes);
			final int iOverallTime = benchmark.run();
			final int iOverallSearches = benchmark.getOverAllSearches();
			final int iOverallTimeInMinutes = (iOverallTime / 1000 / 60);
			chemIndex.shutdown();

			String strNewName = strOutputFileCsv;
			if (strOutputFileCsv.contains("%searchCount%")) {
				strNewName = strNewName.replaceAll("%searchCount%", "" + iOverallSearches);
			}
			if (strOutputFileCsv.contains("%time%")) {
				strNewName = strNewName.replaceAll("%time%", "" + iOverallTimeInMinutes);
			}

			if (!strNewName.equals(strOutputFileCsv) && !(new File(strOutputFileCsv).renameTo(new File(strNewName)))) {
				System.out.println("Unable to rename result file '" + strOutputFileCsv +
						"'with correct overall search count " + iOverallSearches + ".");
			}

			System.out.println("Overall Searches: " + iOverallSearches);
			System.out.println("Overall Search Time: " + iOverallTimeInMinutes + " min");
		}
	}

	private static Properties readConfigFile(final String strFile) throws FileNotFoundException, IOException {
		final Properties defaults = new Properties();
		defaults.put("firstRow", "-1");
		defaults.put("lastRow", "-1");
		defaults.put("searchTypes", "FP,SS,MOL");
		defaults.put("searchesPerType", "1");
		defaults.put("threadCount", "1");
		defaults.put("resultCsvFile", "%querySmilesFile% (%threadCount% threads, %searchesPerType% searches, %searchTypes%, %searchCount% searches done in %time% min).csv");

		final Properties props = new Properties(defaults);
		props.load(new FileInputStream(strFile));

		final String strInputFile = props.getProperty("querySmilesFile");
		if (strInputFile == null) {
			throw new IllegalArgumentException("Config file does not contain the input file. Key querySmilesFile is missing.");
		}
		String strResultFile = props.getProperty("resultCsvFile");
		strResultFile = strResultFile.replaceAll("%querySmilesFile%", props.getProperty("querySmilesFile", ""));
		strResultFile = strResultFile.replaceAll("%indexDir%",  props.getProperty("indexDir", ""));
		strResultFile = strResultFile.replaceAll("%threadCount%",  props.getProperty("threadCount", ""));
		strResultFile = strResultFile.replaceAll("%searchesPerType%",  props.getProperty("searchesPerType", ""));
		strResultFile = strResultFile.replaceAll("%searchTypes%",  props.getProperty("searchTypes", ""));
		strResultFile = strResultFile.replaceAll("%firstRow%",  props.getProperty("firstRow", ""));
		strResultFile = strResultFile.replaceAll("%lastRow%",  props.getProperty("lastRow", ""));
		props.put("resultCsvFile", strResultFile);

		return props;
	}

	public static void main(final String[] argv) throws IOException {
		// Check arguments
		if (argv.length == 0) {
			printInfoAndExit();
		}
		else if ("-index".equals(argv[0]) && argv.length >= 4) {
			final int length = argv.length - 4;
			final String[] arrPKsToIgnore = new String[length];
			System.arraycopy(argv, 4, arrPKsToIgnore, 0, length);
			index(argv[1], argv[2], argv[3], arrPKsToIgnore);
		}
		else if ("-benchmark".equals(argv[0]) && argv.length >= 3) {
			final int length = argv.length - 2;
			final String[] arrConfigFiles = new String[length];
			System.arraycopy(argv, 2, arrConfigFiles, 0, length);
			benchmark(argv[1], arrConfigFiles);
		}
		else {
			printInfoAndExit();
		}
	}
}
