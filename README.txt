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

All commands need to come from a shell where the working directory is the directory where the chemsearchindex.zip file had been unzipped

Running Benchmarking from the Command Line
==========================================
1. Download the sdf file to index from ftp://ftp.ebi.ac.uk/pub/databases/chembl/ChEMBLdb/latest/chembl_14.sdf.gz  

2. Create an index (or add to an existing index)
Important: The last parameter specifies a list of Primary Keys to ignore. The specified ID would currently crash RDKit and the Indexing
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -index index chembl_14.sdf.gz chembl_id CHEMBL1973569

3. Run the benchmarking
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-zinc.leads-1thread.ini
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-zinc.leads-6threads.ini
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-zinc.frags-1thread.ini
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-zinc.frags-6threads.ini
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-fragqueries-1thread.ini
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark -benchmark index benchmarking/benchmark-fragqueries-6threads.ini

4. For further information print the usage info
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.benchmarking.LuceneBenchmark

Exploring ChemIndex with a Demo GUI
===================================
The optional index directory parameter can be the same directory that was used to create an index with the LuceneBenchmark application
java -cp ".;chemsearchindex.jar" org.rdkit.lucene.demo.LuceneDemo [<Index directory>]

The GUI offers the possibility to add files to an index and to search an existing index. "Free Search" does not work properly yet.
The current demo code uses the Avalon Fingerprint. This can be changed in the main() method of the LuceneDemo class.