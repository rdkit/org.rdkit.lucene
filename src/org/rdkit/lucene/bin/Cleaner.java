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

import java.util.concurrent.atomic.AtomicInteger;


class Cleaner implements RDKitObjectCleaner {

	//
	// Statics
	//

	/**
	 * Increasing thread-safe counter wave id assignment used in the context
	 * of cleaning of RDKit objects.
	 */
	private static AtomicInteger g_nextUniqueWaveId = new AtomicInteger(1);

	//
	// Members
	//

	/**
	 * List to register RDKit objects for cleanup. It's important to
	 * initialize this first.
	 */
	private final RDKitCleanupTracker m_rdkitCleanupTracker = new RDKitCleanupTracker();

	/**
	 * Creates a new wave id. This id must be unique in the context of the
	 * overall runtime of the Java VM, at least in the context of the same
	 * class loader and memory area.
	 * 
	 * @return Unique wave id.
	 */
	@Override
	public int createUniqueCleanupWaveId() {
		return g_nextUniqueWaveId.getAndIncrement();
	}

	/**
	 * Registers an RDKit based object, which must have a delete() method
	 * implemented for freeing up resources later. The cleanup will happen
	 * for all registered objects when the method
	 * {@link #cleanupMarkedObjects()} is called. Note: If the same
	 * rdkitObject was already registered for a wave it would be cleaned up
	 * multiple times, which may have negative side effects and may even
	 * cause errors. Hence, always mark an object only once for cleanup, or
	 * - if required in certain situations - call
	 * {@link #markForCleanup(Object, boolean)} and set the last parameter
	 * to true to remove the object from the formerly registered wave.
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called
	 *            to free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when
	 *            not used anymore. Can be null.
	 * 
	 * @return The same object that was passed in. Null, if null was passed
	 *         in.
	 * 
	 * @see #cleanupMarkedObjects(int)
	 */
	@Override
	public <T extends Object> T markForCleanup(final T rdkitObject) {
		return markForCleanup(rdkitObject, 0, false);
	}

	/**
	 * Registers an RDKit based object, which must have a delete() method
	 * implemented for freeing up resources later. The cleanup will happen
	 * for all registered objects when the method
	 * {@link #cleanupMarkedObjects()} is called. Note: If the last
	 * parameter is set to true and the same rdkitObject was already
	 * registered for another wave it will be removed from the former wave
	 * list and will exist only in the wave specified here. This can be
	 * useful for instance, if an object is first marked as part of a wave
	 * and later on it is determined that it needs to live longer (e.g.
	 * without a wave). In this case the first time this method would be
	 * called with a wave id, the second time without wave id (which would
	 * internally be wave = 0).
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called
	 *            to free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when
	 *            not used anymore. Can be null.
	 * @param bRemoveFromOtherWave
	 *            Checks, if the object was registered before with another
	 *            wave id, and remove it from that former wave. Usually this
	 *            should be set to false for performance reasons.
	 * 
	 * @return The same object that was passed in. Null, if null was passed
	 *         in.
	 * 
	 * @see #cleanupMarkedObjects(int)
	 */
	public <T extends Object> T markForCleanup(final T rdkitObject,
			final boolean bRemoveFromOtherWave) {
		return markForCleanup(rdkitObject, 0, bRemoveFromOtherWave);
	}

	/**
	 * Registers an RDKit based object that is used within a certain block
	 * (wave). $ This object must have a delete() method implemented for
	 * freeing up resources later. The cleanup will happen for all
	 * registered objects when the method {@link #cleanupMarkedObjects(int)}
	 * is called with the same wave. Note: If the same rdkitObject was
	 * already registered for another wave (or no wave) it would be cleaned
	 * up multiple times, which may have negative side effects and may even
	 * cause errors. Hence, always mark an object only once for cleanup, or
	 * - if required in certain situations - call
	 * {@link #markForCleanup(Object, int, boolean)} and set the last
	 * parameter to true to remove the object from the formerly registered
	 * wave.
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called
	 *            to free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when
	 *            not used anymore. Can be null.
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 * 
	 * @return The same object that was passed in. Null, if null was passed
	 *         in.
	 */
	@Override
	public <T extends Object> T markForCleanup(final T rdkitObject,
			final int wave) {
		return markForCleanup(rdkitObject, wave, false);
	}

	/**
	 * Registers an RDKit based object that is used within a certain block
	 * (wave). $ This object must have a delete() method implemented for
	 * freeing up resources later. The cleanup will happen for all
	 * registered objects when the method {@link #cleanupMarkedObjects(int)}
	 * is called with the same wave. Note: If the last parameter is set to
	 * true and the same rdkitObject was already registered for another wave
	 * (or no wave) it will be removed from the former wave list and will
	 * exist only in the wave specified here. This can be useful for
	 * instance, if an object is first marked as part of a wave and later on
	 * it is determined that it needs to live longer (e.g. without a wave).
	 * In this case the first time this method would be called with a wave
	 * id, the second time without wave id (which would internally be wave =
	 * 0).
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called
	 *            to free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when
	 *            not used anymore. Can be null.
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 * @param bRemoveFromOtherWave
	 *            Checks, if the object was registered before with another
	 *            wave id, and remove it from that former wave. Usually this
	 *            should be set to false for performance reasons.
	 * 
	 * @return The same object that was passed in. Null, if null was passed
	 *         in.
	 */
	public <T extends Object> T markForCleanup(final T rdkitObject,
			final int wave, final boolean bRemoveFromOtherWave) {
		return m_rdkitCleanupTracker.markForCleanup(rdkitObject, wave,
				bRemoveFromOtherWave);
	}

	/**
	 * Frees resources for all objects that have been registered prior to
	 * this last call using the method {@link #cleanupMarkedObjects()}.
	 */
	@Override
	public void cleanupMarkedObjects() {
		m_rdkitCleanupTracker.cleanupMarkedObjects();
	}

	/**
	 * Frees resources for all objects that have been registered prior to
	 * this last call for a certain wave using the method
	 * {@link #cleanupMarkedObjects(int)}.
	 * 
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 */
	@Override
	public void cleanupMarkedObjects(final int wave) {
		m_rdkitCleanupTracker.cleanupMarkedObjects(wave);
	}

	/**
	 * Removes all resources for all objects that have been registered prior
	 * to this last call using the method {@link #cleanupMarkedObjects()},
	 * but delayes the cleanup process. It basically moves the objects of
	 * interest into quarantine.
	 */
	public void quarantineAndCleanupMarkedObjects() {
		m_rdkitCleanupTracker.quarantineAndCleanupMarkedObjects();
	}
}