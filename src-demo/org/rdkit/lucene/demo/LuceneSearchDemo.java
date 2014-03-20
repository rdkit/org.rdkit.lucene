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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

import org.RDKit.RDKFuncs;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.store.NIOFSDirectory;
import org.rdkit.lucene.AnalyzerFactory;
import org.rdkit.lucene.ChemicalIndex;
import org.rdkit.lucene.IndexListener;
import org.rdkit.lucene.StandardAnalyzerFactory;
import org.rdkit.lucene.bin.RDKit;
import org.rdkit.lucene.fingerprint.DefaultFingerprintFactory;
import org.rdkit.lucene.fingerprint.DefaultFingerprintSettings;
import org.rdkit.lucene.fingerprint.FingerprintFactory;
import org.rdkit.lucene.fingerprint.FingerprintType;
import org.rdkit.lucene.util.LayoutUtils;

public class LuceneSearchDemo extends JPanel {

	//
	// Constants
	//

	/** Serialnumber. */
	private static final long serialVersionUID = -4861261326474033065L;

	/** The logger instance. */
	private static final Logger LOGGER = Logger.getLogger(LuceneSearchDemo.class.getName());

	//
	// Members
	//

	private final ChemicalIndex m_index;
	private final SmilesIconFactory m_smilesIconFactory;
	private File m_dirCurrent;
	private final AtomicInteger m_iAddedMoleculeCount;
	private String m_strLastSelectedPK;

	// Register panel
	private JTextField m_tfFilename;
	private JButton m_btnBrowse;
	private JButton m_btnAdd;
	private JTextField m_tfPrimaryKeyField;
	private JTextField m_tfIgnoreUpToPK;
	private JTextField m_tfIgnorePKs;
	private JLabel m_lbMoleculeCount;

	// Search panel
	private JTextField m_tfSearch;
	private JButton m_btnShowStructure;
	private JButton m_btnSearch;
	private JButton m_btnSearchByName;
	private JButton m_btnSearchByMolecule;
	private JButton m_btnSearchByFingerprintSimilarity;
	private JButton m_btnSearchBySubstructure;

	// Result panel
	private JLabel m_lbSearchTerms;
	private JList m_lResults;
	private JLabel m_lbHitCount;
	private JLabel m_lbSearchTime;
	private JLabel m_lbDetailsSmiles;
	private JLabel m_lbDetailsStructureGif;

	// Status panel
	private JLabel m_lbStatus;

	//
	// Constructor
	//

	public LuceneSearchDemo(final ChemicalIndex index, final SmilesIconFactory smilesIconFactory) {
		super(new GridBagLayout());

		if (index == null) {
			throw new IllegalArgumentException("Chemical Index must not be null.");
		}

		m_iAddedMoleculeCount = new AtomicInteger();
		m_index = index;
		m_index.addIndexListener(new IndexListener() {

			private final StringBuilder m_sb = new StringBuilder(200);

			@Override
			public synchronized void onMoleculeAdded(final String strPK, final String strSmiles) {
				final int iCount = m_iAddedMoleculeCount.incrementAndGet();
				if (iCount % 100 == 0) {
					synchronized (m_sb) {
						m_sb.setLength(0);
						m_sb.append("Added ").append(iCount).
						append(" molecules (last one: ").append(strPK).append(")");

						setStatus(m_sb.toString());
					}
				}
			}
		});
		m_dirCurrent = new File(System.getProperty("user.dir"));
		m_smilesIconFactory = smilesIconFactory;

		LayoutUtils.constrain(this, createIndexPanel(), 0, 0, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				10, 10, 0, 10);
		LayoutUtils.constrain(this, createSearchPanel(), 0, 1, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				10, 10, 0, 10);
		LayoutUtils.constrain(this, createResultPanel(), 0, 2, LayoutUtils.REMAINDER, 1,
				LayoutUtils.BOTH, LayoutUtils.NORTHWEST, 1.0d, 1.0d,
				10, 10, 0, 10);
		LayoutUtils.constrain(this, createStatusPanel(), 0, 3, LayoutUtils.REMAINDER, LayoutUtils.REMAINDER,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				10, 10, 10, 10);

		onUpdateIndexStatistics();
	}

	public void addFileToIndex(final File sdfFile, final String strFieldPrimaryKey,
			final String strIgnoreUpToPK, final Set<String> setIgnorePKs) {
		doJob("Indexing", new Runnable() {
			@Override
			public void run() {
				try {
					m_iAddedMoleculeCount.set(0);
					m_index.addSDFFileToIndex(sdfFile, strFieldPrimaryKey, strIgnoreUpToPK, setIgnorePKs);
					onUpdateIndexStatistics();
				}
				catch (final IOException exc) {
					LOGGER.log(Level.SEVERE, "Unable to add file to index.", exc);
				}
			}
		});
	}

	//
	// Protected Methods
	//

	protected JPanel createIndexPanel() {
		final JPanel panel = new JPanel(new GridBagLayout());

		m_tfFilename = new JTextField();
		m_btnBrowse = new JButton("Browse...");
		m_btnAdd = new JButton("Add to Index");
		m_tfPrimaryKeyField = new JTextField("chembl_id");
		m_tfIgnoreUpToPK = new JTextField();
		m_tfIgnorePKs = new JTextField();
		m_lbMoleculeCount = new JLabel("0");

		final JPanel pOptions = new JPanel(new GridBagLayout());
		int iCol = 0;
		int iRow = 0;
		LayoutUtils.constrain(pOptions, new JLabel("SDF field name of the primary key to be used:"),
				iCol++, iRow, 1, 1,
				LayoutUtils.NONE, LayoutUtils.WEST, 0.0d, 0.0d,
				0, 0, 0, 0);
		LayoutUtils.constrain(pOptions, m_tfPrimaryKeyField,
				iCol++, iRow, 1, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				0, 10, 0, 0);
		LayoutUtils.constrain(pOptions, new JLabel("Ignore up to this primary key:"),
				iCol++, iRow, 1, 1,
				LayoutUtils.NONE, LayoutUtils.WEST, 0.0d, 0.0d,
				0, 20, 0, 0);
		LayoutUtils.constrain(pOptions, m_tfIgnoreUpToPK,
				iCol++, iRow++, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				0, 10, 0, 0);
		iCol = 0;
		LayoutUtils.constrain(pOptions, new JLabel("Ignore the following primary keys:"),
				iCol++, iRow, 1, LayoutUtils.REMAINDER,
				LayoutUtils.NONE, LayoutUtils.WEST, 0.0d, 0.0d,
				10, 0, 0, 0);
		LayoutUtils.constrain(pOptions, m_tfIgnorePKs,
				iCol++, iRow++, LayoutUtils.REMAINDER, LayoutUtils.REMAINDER,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				10, 10, 0, 0);

		final JPanel pStats = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pStats.add(new JLabel("Currently there are"));
		pStats.add(m_lbMoleculeCount);
		pStats.add(new JLabel("molecules in the index."));

		LayoutUtils.constrain(panel, new JLabel("Add SDF File to Chemical Index:"),
				0, 0, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				0, 0, 5, 0);
		LayoutUtils.constrain(panel, m_tfFilename, 0, 1, 1, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				0, 0, 0, 0);
		LayoutUtils.constrain(panel, m_btnBrowse, 1, 1, 1, 1,
				LayoutUtils.NONE, LayoutUtils.WEST, 0.0d, 0.0d,
				0, 10, 0, 0);
		LayoutUtils.constrain(panel, m_btnAdd, 2, 1, LayoutUtils.REMAINDER, 1,
				LayoutUtils.NONE, LayoutUtils.WEST, 0.0d, 0.0d,
				0, 10, 0, 0);
		LayoutUtils.constrain(panel, pOptions, 0, 2, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				5, 0, 5, 0);
		LayoutUtils.constrain(panel, pStats, 0, 3, LayoutUtils.REMAINDER, LayoutUtils.REMAINDER,
				LayoutUtils.HORIZONTAL, LayoutUtils.WEST, 1.0d, 0.0d,
				0, 0, 0, 0);

		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Register"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		m_btnBrowse.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onBrowse();
			}
		});

		m_btnAdd.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onAddToIndex();
			}
		});

		return panel;
	}

	protected JPanel createSearchPanel() {
		final JPanel panel = new JPanel(new GridBagLayout());

		m_tfSearch = new JTextField();
		m_btnShowStructure = new JButton("Show SMILES...");
		m_btnSearch = new JButton("Free Search");
		m_btnSearchByName = new JButton("Search By Name");
		m_btnSearchByMolecule = new JButton("Search By Molecule");
		m_btnSearchByFingerprintSimilarity = new JButton("Search By FP Match");
		m_btnSearchBySubstructure = new JButton("Search By Substructure");

		final JPanel pSearchTerm = new JPanel(new BorderLayout(5, 5));
		pSearchTerm.add(m_tfSearch, BorderLayout.CENTER);
		pSearchTerm.add(m_btnShowStructure, BorderLayout.EAST);

		final JPanel pButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		pButtons.add(m_btnSearch);
		pButtons.add(m_btnSearchByName);
		pButtons.add(m_btnSearchByMolecule);
		pButtons.add(m_btnSearchByFingerprintSimilarity);
		pButtons.add(m_btnSearchBySubstructure);

		LayoutUtils.constrain(panel, new JLabel("Enter Search Term(s):"),
				0, 0, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				0, 0, 5, 0);
		LayoutUtils.constrain(panel, pSearchTerm, 0, 1, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				0, 0, 0, 0);
		LayoutUtils.constrain(panel, pButtons, 0, 2, LayoutUtils.REMAINDER, LayoutUtils.REMAINDER,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				0, 0, 0, 0);

		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Search"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		m_btnShowStructure.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onShowSmiles();
			}
		});

		m_btnSearch.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onSearch();
			}
		});

		m_btnSearchByName.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onSearchByName();
			}
		});

		m_btnSearchByMolecule.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onSearchByMolecule();
			}
		});

		m_btnSearchByFingerprintSimilarity.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onSearchByFingerprintSimilarity();
			}
		});

		m_btnSearchBySubstructure.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onSearchBySubstructure();
			}
		});

		return panel;
	}

	protected JPanel createResultPanel() {
		final JPanel panel = new JPanel(new GridBagLayout());

		m_lResults = new JList(new DefaultListModel());
		m_lbHitCount = new JLabel("0");
		m_lbSearchTime = new JLabel("0");

		m_lbSearchTerms = new JLabel("no search yet");
		m_lbDetailsSmiles = new JLabel();
		m_lbDetailsStructureGif = new JLabel((Icon)null, SwingConstants.CENTER);

		m_lResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		m_lResults.addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(final ListSelectionEvent e) {
				final int index = m_lResults.getSelectedIndex();
				final ListModel model = m_lResults.getModel();
				String strPK = null;
				if (index >= 0 && index < model.getSize()) {
					strPK = (String)model.getElementAt(index);
				}
				onUpdateResultDetails(strPK);
			}
		});

		final JScrollPane scrollPane = new JScrollPane(m_lResults);
		scrollPane.setPreferredSize(new Dimension(300, 300));
		scrollPane.setMinimumSize(new Dimension(300, 300));

		final JPanel pSearchTerms = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pSearchTerms.add(new JLabel("Execution of"));
		pSearchTerms.add(m_lbSearchTerms);

		final JPanel pDetails = new JPanel(new BorderLayout(5, 5));
		final JPanel pStructure = new JPanel(new BorderLayout());
		pStructure.setBackground(Color.WHITE);
		pStructure.setBorder(BorderFactory.createEtchedBorder());
		pStructure.add(m_lbDetailsStructureGif, BorderLayout.CENTER);
		pDetails.add(m_lbDetailsSmiles, BorderLayout.NORTH);
		pDetails.add(pStructure, BorderLayout.CENTER);
		pDetails.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Result Details"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		pDetails.setPreferredSize(new Dimension(300, 300));
		pDetails.setMinimumSize(new Dimension(300, 300));

		final JPanel pStats = new JPanel(new FlowLayout(FlowLayout.LEFT));
		pStats.add(m_lbHitCount);
		pStats.add(new JLabel("Hits in"));
		pStats.add(m_lbSearchTime);
		pStats.add(new JLabel("ms."));

		LayoutUtils.constrain(panel, pSearchTerms, 0, 0, LayoutUtils.REMAINDER, 1,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				0, 0, 10, 0);
		LayoutUtils.constrain(panel, scrollPane, 0, 1, 1, 1,
				LayoutUtils.BOTH, LayoutUtils.CENTER, 0.5d, 1.0d,
				0, 0, 0, 0);
		LayoutUtils.constrain(panel, pDetails, 1, 1, LayoutUtils.REMAINDER, 1,
				LayoutUtils.BOTH, LayoutUtils.CENTER, 0.5d, 1.0d,
				0, 10, 0, 0);
		LayoutUtils.constrain(panel, pStats, 0, 2, LayoutUtils.REMAINDER, LayoutUtils.REMAINDER,
				LayoutUtils.HORIZONTAL, LayoutUtils.NORTHWEST, 1.0d, 0.0d,
				0, 0, 0, 0);

		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Search Results"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		onUpdateResultDetails(null);

		return panel;
	}

	protected JPanel createStatusPanel() {
		final JPanel panel = new JPanel(new BorderLayout());

		m_lbStatus = new JLabel("Welcome");
		panel.add(m_lbStatus, BorderLayout.CENTER);

		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(""),
				BorderFactory.createEmptyBorder(1, 1, 1, 1)));

		return panel;
	}

	/**
	 * Tries to create an SMILES image using the configured SmilesIconFactory and makes it available
	 * as icon.
	 * 
	 * @param strSmiles	Smiles string for the requested image.
	 * @param width		Width of the image to be produced. Set to -1 to get the default size.
	 * @param height	Height of the image to be produced. Set to -1 to get the default size.
	 * 
	 * @return Icon with SMILES. Null, if image cannot be generated.
	 */
	protected Icon getSmilesIcon(final String strSmiles, final int width, final int height) {
		Icon icon = null;

		if (m_smilesIconFactory != null) {
			try {
				icon = m_smilesIconFactory.createSmilesIcon(strSmiles, width, height);
			}
			catch (final Exception exc) {
				LOGGER.log(Level.SEVERE, "Unable to create SMILES icon for " + strSmiles, exc);
			}
		}

		return icon;
	}

	protected void setStatus(final String strStatus) {
		m_lbStatus.setText(strStatus);
	}

	protected void doJob(final String strJobName, final Runnable runCode) {
		// Disable GUI
		final JPanel panel = this;

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				m_btnAdd.setEnabled(false);
				m_btnBrowse.setEnabled(false);
				m_btnShowStructure.setEnabled(false);
				m_btnSearch.setEnabled(false);
				m_btnSearchByName.setEnabled(false);
				m_btnSearchByMolecule.setEnabled(false);
				m_btnSearchByFingerprintSimilarity.setEnabled(false);
				m_btnSearchBySubstructure.setEnabled(false);
				m_tfFilename.setEnabled(false);
				m_tfPrimaryKeyField.setEnabled(false);
				m_tfIgnoreUpToPK.setEnabled(false);
				m_tfIgnorePKs.setEnabled(false);
				m_tfSearch.setEnabled(false);
				panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			}
		});

		final Thread worker = new Thread(new Runnable() {

			@Override
			public void run() {
				setStatus(strJobName + " started");

				try {
					runCode.run();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Job execution failed.", exc);
				}
				finally {
					// Re-enable GUI
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							m_btnAdd.setEnabled(true);
							m_btnBrowse.setEnabled(true);
							m_btnShowStructure.setEnabled(true);
							m_btnSearch.setEnabled(true);
							m_btnSearchByName.setEnabled(true);
							m_btnSearchByMolecule.setEnabled(true);
							m_btnSearchByFingerprintSimilarity.setEnabled(true);
							m_btnSearchBySubstructure.setEnabled(true);
							m_tfFilename.setEnabled(true);
							m_tfPrimaryKeyField.setEnabled(true);
							m_tfIgnoreUpToPK.setEnabled(true);
							m_tfIgnorePKs.setEnabled(true);
							m_tfSearch.setEnabled(true);
							panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

							setStatus(strJobName + " finished");
						}
					});
				}
			}
		}, "Worker Thread");

		worker.start();
	}

	protected void onBrowse() {
		File fileSelected = null;
		final JFileChooser dlg = new JFileChooser(m_dirCurrent);
		dlg.setFileFilter(new FileFilter() {

			@Override
			public String getDescription() {
				return "SDF Files";
			}

			@Override
			public boolean accept(final File f) {
				return (f != null && (f.isDirectory() ||
						(f.isFile() && (f.getName().endsWith(".sdf") ||
								f.getName().endsWith(".sd") ||
								f.getName().endsWith(".sdf.gz") ||
								f.getName().endsWith(".sd.gz")))));
			}
		});
		if (dlg.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			fileSelected = dlg.getSelectedFile();
		}

		m_dirCurrent = dlg.getCurrentDirectory();

		if (fileSelected != null) {
			m_tfFilename.setText(fileSelected.getAbsolutePath());
		}
	}

	protected void onAddToIndex() {
		final String strIgnoreUpToPK = m_tfIgnoreUpToPK.getText().trim();
		final String strIgnorePKs = m_tfIgnorePKs.getText().trim();
		addFileToIndex(new File(m_tfFilename.getText()), m_tfPrimaryKeyField.getText(),
				strIgnoreUpToPK.isEmpty() ? null : strIgnoreUpToPK, createSet(strIgnorePKs));
	}

	protected void onShowSmiles() {
		final LuceneSearchDemo searchDemoPanel = this;
		doJob("Show Search SMILES", new Runnable() {
			@Override
			public void run() {
				try {
					final String strSmiles = m_tfSearch.getText();
					final Icon iconSmiles = getSmilesIcon(strSmiles, 300, 250);

					final JLabel lbSearchSmiles = new JLabel(strSmiles);
					final JLabel lbSearchStructureGif = new JLabel();
					if (iconSmiles == null) {
						lbSearchStructureGif.setText("Unable to render SMILES");
					}
					else {
						lbSearchStructureGif.setIcon(iconSmiles);
					}

					final JPanel pDetails = new JPanel(new BorderLayout(5, 5));
					final JPanel pStructure = new JPanel(new BorderLayout());
					pStructure.setBackground(Color.WHITE);
					pStructure.setBorder(BorderFactory.createEtchedBorder());
					pStructure.add(lbSearchStructureGif, BorderLayout.CENTER);
					pDetails.add(lbSearchSmiles, BorderLayout.NORTH);
					pDetails.add(pStructure, BorderLayout.CENTER);
					pDetails.setBorder(BorderFactory.createCompoundBorder(
							BorderFactory.createTitledBorder("Search Structure Details"),
							BorderFactory.createEmptyBorder(5, 5, 5, 5)));
					pDetails.setPreferredSize(new Dimension(300, 300));
					pDetails.setMinimumSize(new Dimension(300, 300));

					final Dialog dialogParent = (Dialog)SwingUtilities.getAncestorOfClass(Dialog.class, searchDemoPanel);
					final Frame frameParent = (Frame)SwingUtilities.getAncestorOfClass(Frame.class, searchDemoPanel);
					final JDialog dlg = (dialogParent != null ? new JDialog(dialogParent, false) : new JDialog(frameParent, false));
					dlg.setResizable(false);
					dlg.setTitle("Search SMILES: " + strSmiles);
					dlg.setLayout(new BorderLayout());
					dlg.add(pDetails, BorderLayout.CENTER);
					final WindowListener wndListener = new WindowAdapter() {

						boolean m_bDisposed = false;

						@Override
						public void windowClosing(final WindowEvent e) {
							dlg.setVisible(false);

							if (!m_bDisposed) {
								m_bDisposed = true;
								dlg.dispose();
							}
						}
					};
					dlg.addWindowListener(wndListener);
					if (dialogParent != null) {
						dialogParent.addWindowListener(wndListener);
					}
					else if (frameParent != null) {
						frameParent.addWindowListener(wndListener);
					}
					dlg.pack();
					dlg.setLocationRelativeTo(dialogParent != null ? dialogParent : frameParent);
					dlg.setVisible(true); // Does not block
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Showing search SMILES failed.", exc);
				}
			}
		});
	}

	protected void onSearch() {
		doJob("Search", new Runnable() {
			@Override
			public void run() {
				try {
					final long lStart = System.currentTimeMillis();
					final TopDocsCollector<ScoreDoc> collector =
							m_index.searchMolecules(m_tfSearch.getText(), 1000000);
					final long lEnd = System.currentTimeMillis();
					onUpdateResults("Free Search", collector, (int)(lEnd - lStart));
					onUpdateIndexStatistics();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search failed.", exc);
				}
			}
		});
	}

	protected void onSearchByName() {
		doJob("Search", new Runnable() {
			@Override
			public void run() {
				try {
					final long lStart = System.currentTimeMillis();
					final TopDocsCollector<ScoreDoc> collector =
							m_index.searchMoleculesByName(m_tfSearch.getText(), 1000000);
					final long lEnd = System.currentTimeMillis();
					onUpdateResults("Search By Name", collector, (int)(lEnd - lStart));
					onUpdateIndexStatistics();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search failed.", exc);
				}
			}
		});
	}

	protected void onSearchByMolecule() {
		doJob("Search", new Runnable() {
			@Override
			public void run() {
				try {
					final long lStart = System.currentTimeMillis();
					final TopDocsCollector<ScoreDoc> collector =
							m_index.searchExactMolecules(m_tfSearch.getText(), 1000000);
					final long lEnd = System.currentTimeMillis();
					onUpdateResults("Search By Molecule", collector, (int)(lEnd - lStart));
					onUpdateIndexStatistics();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search failed.", exc);
				}
			}
		});
	}

	protected void onSearchByFingerprintSimilarity() {
		doJob("Search", new Runnable() {
			@Override
			public void run() {
				try {
					final long lStart = System.currentTimeMillis();
					final TopDocsCollector<ScoreDoc> collector =
							m_index.searchMoleculesByFingerprintMatch(m_tfSearch.getText(), 1000000);
					final long lEnd = System.currentTimeMillis();
					onUpdateResults("Search By FP Match", collector, (int)(lEnd - lStart));
					onUpdateIndexStatistics();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search failed.", exc);
				}
			}
		});
	}

	protected void onSearchBySubstructure() {
		doJob("Search", new Runnable() {
			@Override
			public void run() {
				try {
					final long lStart = System.currentTimeMillis();
					final TopDocsCollector<ScoreDoc> collector =
							m_index.searchMoleculesWithSubstructure(m_tfSearch.getText(), 10000);
					final long lEnd = System.currentTimeMillis();
					onUpdateResults("Search By Substructure", collector, (int)(lEnd - lStart));
					onUpdateIndexStatistics();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Search failed.", exc);
				}
			}
		});
	}

	protected void onUpdateIndexStatistics() {
		try {
			final int iCount = m_index.getIndexedMoleculeCount();
			m_lbMoleculeCount.setText("" + (iCount > 0 ? iCount : "no"));
		}
		catch (final IOException exc) {
			LOGGER.log(Level.SEVERE, "Updating of index statistics failed.", exc);
			m_lbMoleculeCount.setText("?");
		}
	}

	protected void onUpdateResults(final String strSearchType, final TopDocsCollector<ScoreDoc> collector,
			final int iSearchTime) {
		final DefaultListModel modelOld = (DefaultListModel)m_lResults.getModel();
		modelOld.removeAllElements();

		final DefaultListModel modelNew = new DefaultListModel();
		int iTotalHits = 0;

		if (collector != null) {
			try {
				final String[] arrPKs = m_index.getPrimaryKeysForSearchHits(collector);
				iTotalHits = arrPKs.length;
				for (final String pk : arrPKs) {
					modelNew.addElement(pk);
				}
			}
			catch (final Exception exc) {
				modelNew.addElement("Unable to deliver search results.");
				LOGGER.log(Level.SEVERE, "Unable to deliver search results.", exc);
			}
		}

		m_lbSearchTerms.setText(strSearchType + ": " + m_tfSearch.getText());
		m_lResults.setModel(modelNew);
		m_lbHitCount.setText("" + iTotalHits);
		m_lbSearchTime.setText("" + iSearchTime);

		if (!modelNew.isEmpty()) {
			m_lResults.setSelectedIndex(0);
		}
	}

	protected void onUpdateResultDetails(final String strPK) {
		if (strPK == null) {
			m_lbDetailsSmiles.setText("Select a result item to show details.");
			m_lbDetailsStructureGif.setIcon(null);
			m_lbDetailsStructureGif.setText(null);
			m_strLastSelectedPK = null;
		}
		else if (!strPK.equals(m_strLastSelectedPK)) {
			m_strLastSelectedPK = strPK;
			boolean bNoInformation = false;
			m_lbDetailsStructureGif.setText(null);
			try {
				final Document doc = m_index.searchMoleculeByPK(strPK);
				if (doc != null) {
					final String strSmiles = doc.get(ChemicalIndex.FIELD_SMILES);
					if (strSmiles != null && !strSmiles.trim().isEmpty()) {
						final Dimension dim = m_lbDetailsSmiles.getParent().getSize();
						final Icon iconStructure = getSmilesIcon(strSmiles, dim.width-50, dim.height-50);
						m_lbDetailsSmiles.setText(strSmiles);
						m_lbDetailsSmiles.setToolTipText(strSmiles);
						m_lbDetailsStructureGif.setIcon(iconStructure);
						if (iconStructure == null) {
							m_lbDetailsStructureGif.setText("Unable to render SMILES");
						}
					}
					else {
						bNoInformation = true;
					}
				}
				else {
					bNoInformation = true;
				}
			}
			catch (final Exception exc) {
				LOGGER.fine("No information available for PK " + strPK);
				bNoInformation = true;
			}

			if (bNoInformation) {
				m_lbDetailsSmiles.setText("No information available.");
				m_lbDetailsStructureGif.setIcon(null);
			}
		}
	}

	//
	// Private Methods
	//

	private HashSet<String> createSet(final String str) {
		HashSet<String> setRet = null;

		if (str != null) {
			final StringTokenizer st = new StringTokenizer(str, ",");
			if (st.countTokens() > 0) {
				setRet = new HashSet<String>(st.countTokens());
				while (st.hasMoreTokens()) {
					setRet.add(st.nextToken().trim());
				}
			}
		}

		return setRet;
	}

	//
	// Static Public Methods
	//

	public static void main(final String[] args) throws IOException {
		// Activate RDKit
		RDKit.activate();

		// Set System L&F
		try {
			UIManager.setLookAndFeel(
					UIManager.getSystemLookAndFeelClassName());
		}
		catch (final Exception e) {
			// Ignored, it is just cosmetic
		}

		// Setup Lucene Chemical Index
		final File dirIndex = new File(args.length > 0 ? args[0] : "index");
		ChemicalIndex.prepareIndexDirectory(dirIndex, true);
		ChemicalIndex index = null;

		// Define here how fingerprints shall be used
		final FingerprintFactory fingerprintFactory = new DefaultFingerprintFactory(
				// Define structure fingerprint settings (used when indexing molecules)
				new DefaultFingerprintSettings(FingerprintType.avalon)
				.setNumBits(512)
				.setAvalonQueryFlag(0)
				.setAvalonBitFlags(RDKFuncs.getAvalonSSSBits()),
				// Define query fingerprint settings (used when searching molecules)
				new DefaultFingerprintSettings(FingerprintType.avalon)
				.setNumBits(512)
				.setAvalonQueryFlag(1)
				.setAvalonBitFlags(RDKFuncs.getAvalonSSSBits()));

		final AnalyzerFactory analyzerFactory = new StandardAnalyzerFactory();

		final SmilesIconFactory smilesIconFactory = new AvalonSmilesIconFactory(
				"http://web.global.nibr.novartis.net/services/depicter/mol-renderer/.png?smiles=%VALUE%&w=%WIDTH%&h=%HEIGHT%");

		try {
			index = new ChemicalIndex(new NIOFSDirectory(dirIndex), analyzerFactory,
					fingerprintFactory, null);

			// Prepare GUI
			final LuceneSearchDemo searchPanel = new LuceneSearchDemo(index, smilesIconFactory);
			final JDialog dlg = new JDialog((Frame)null, true);
			dlg.setLayout(new BorderLayout(0, 0));

			final JButton btnExit = new JButton("Exit");
			btnExit.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					dlg.setVisible(false);
				}
			});

			final JPanel panelButton = new JPanel(new GridBagLayout());
			LayoutUtils.constrain(panelButton, btnExit, 0, 0, 1, 1,
					LayoutUtils.NONE, LayoutUtils.EAST, 0.0d, 0.0d,
					10, 10, 10, 10);

			dlg.add(searchPanel, BorderLayout.CENTER);
			dlg.add(panelButton, BorderLayout.SOUTH);
			dlg.setTitle("Lucene Search Demo");
			dlg.pack();
			dlg.setLocationRelativeTo(null);
			dlg.setVisible(true); // Blocks
			dlg.dispose();
		}
		finally {
			if (index != null) {
				try {
					index.shutdown();
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE, "Index shutdown caused exception", exc);
				}
			}
		}
	}

}
