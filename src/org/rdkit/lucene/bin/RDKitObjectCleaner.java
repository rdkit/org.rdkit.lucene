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
package org.rdkit.lucene.bin;


/**
 * This interface defines methods to register RDKit objects, which are subject of later
 * cleanup.
 * 
 * @author Manuel Schwarze
 */
public interface RDKitObjectCleaner {

	/**
	 * Creates a new wave id. This id must be unique in the context of the overall runtime
	 * of the Java VM, at least in the context of the same class loader and memory area.
	 * 
	 * @return Unique wave id.
	 */
	int createUniqueCleanupWaveId();

	/**
	 * Registers an RDKit based object, which must have a delete() method implemented
	 * for freeing up resources later. The cleanup will happen for all registered
	 * objects when the method {@link #cleanupMarkedObjects()} is called.
	 * 
	 * @param <T> Any class that implements a delete() method to be called to free up resources.
	 * @param rdkitObject An RDKit related object that should free resources when not
	 * 		used anymore. Can be null.
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 * 
	 * @see #markForCleanup(Object, int)
	 */
	<T extends Object> T markForCleanup(T rdkitObject);

	/**
	 * Registers an RDKit based object that is used within a certain block (wave). $
	 * This object must have a delete() method implemented for freeing up resources later.
	 * The cleanup will happen for all registered objects when the method
	 * {@link #cleanupMarkedObjects(int)} is called with the same wave.
	 * 
	 * @param <T> Any class that implements a delete() method to be called to free up resources.
	 * @param rdkitObject An RDKit related object that should free resources when not
	 * 		used anymore. Can be null.
	 * @param wave A number that identifies objects registered for a certain "wave".
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 * 
	 * @see #markForCleanup(Object)
	 */
	<T extends Object> T markForCleanup(T rdkitObject, int wave);

	/**
	 * Frees resources for all objects that have been registered prior to this last
	 * call using the method {@link #cleanupMarkedObjects()}.
	 */
	void cleanupMarkedObjects();

	/**
	 * Frees resources for all objects that have been registered prior to this last
	 * call for a certain wave using the method {@link #cleanupMarkedObjects(int)}.
	 * 
	 * @param wave A number that identifies objects registered for a certain "wave".
	 */
	void cleanupMarkedObjects(int wave);

}
