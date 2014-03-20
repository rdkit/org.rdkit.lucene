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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class keeps track of RDKit objects which require cleanup when not
 * needed anymore.
 * 
 * @author Manuel Schwarze
 */
class RDKitCleanupTracker extends HashMap<Integer, List<Object>> {

	//
	// Constants
	//

	/** Serial number. */
	private static final long serialVersionUID = 270959635380434185L;

	/** The logger instance. */
	protected static final Logger LOGGER = Logger.getLogger(RDKitCleanupTracker.class.getName());

	//
	// Constructors
	//

	/**
	 * Creates a new RDKitCleanup tracker.
	 */
	public RDKitCleanupTracker() {
		super();
	}

	/**
	 * Creates a new RDKitCleanup tracker.
	 * 
	 * @param initialCapacity
	 * @param loadFactor
	 */
	public RDKitCleanupTracker(final int initialCapacity,
			final float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	/**
	 * Creates a new RDKitCleanup tracker.
	 * 
	 * @param initialCapacity
	 */
	public RDKitCleanupTracker(final int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * Creates a copy of an existing RDKitCleanupTracker object.
	 * 
	 * @param existing
	 *            The existing object. Must not be null.
	 */
	private RDKitCleanupTracker(final RDKitCleanupTracker existing) {
		super(existing);
	}

	//
	// Public Methods
	//

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
	public synchronized <T extends Object> T markForCleanup(
			final T rdkitObject, final int wave,
			final boolean bRemoveFromOtherWave) {
		if (rdkitObject != null) {

			// Remove object from any other list, if desired (cost
			// performance!)
			if (bRemoveFromOtherWave) {

				// Loop through all waves to find the rdkitObject - we
				// create a copy here, because
				// we may remove empty wave lists which may blow up out
				// iterator
				for (final int waveExisting : new HashSet<Integer>(keySet())) {
					final List<Object> list = get(waveExisting);
					if (list.remove(rdkitObject) && list.isEmpty()) {
						remove(waveExisting);
					}
				}
			}

			// Get the list of the target wave
			List<Object> list = get(wave);

			// Create a wave list, if not found yet
			if (list == null) {
				list = new ArrayList<Object>();
				put(wave, list);
			}

			// Add the object only once
			if (!list.contains(rdkitObject)) {
				list.add(rdkitObject);
			}
		}

		return rdkitObject;
	}

	/**
	 * Frees resources for all objects that have been registered prior to
	 * this last call using the method {@link #cleanupMarkedObjects()}.
	 */
	public synchronized void cleanupMarkedObjects() {
		// Loop through all waves for cleanup - we create a copy here,
		// because
		// the cleanupMarkedObjects method will remove items from our map
		for (final int wave : new HashSet<Integer>(keySet())) {
			cleanupMarkedObjects(wave);
		}
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
	public synchronized void cleanupMarkedObjects(final int wave) {
		// Find the right wave list, if not found yet
		final List<Object> list = get(wave);

		// If wave list was found, free all objects in it
		if (list != null) {
			for (final Object objForCleanup : list) {
				Class<?> clazz = null;

				try {
					clazz = objForCleanup.getClass();
					final Method method = clazz.getMethod("delete");
					method.invoke(objForCleanup);
				}
				catch (final NoSuchMethodException excNoSuchMethod) {
					LOGGER.log(Level.SEVERE,
							"An object had been registered for cleanup (delete() call), "
									+ "which does not provide a delete() method."
									+ (clazz == null ? ""
											: " It's of class "
											+ clazz.getName() + "."),
											excNoSuchMethod.getCause());
				}
				catch (final SecurityException excSecurity) {
					LOGGER.log(Level.SEVERE,
							"An object had been registered for cleanup (delete() call), "
									+ "which is not accessible for security reasons."
									+ (clazz == null ? ""
											: " It's of class "
											+ clazz.getName() + "."),
											excSecurity.getCause());
				}
				catch (final Exception exc) {
					LOGGER.log(Level.SEVERE,
							"Cleaning up a registered object (via delete() call) failed."
									+ (clazz == null ? ""
											: " It's of class "
											+ clazz.getName() + "."),
											exc.getCause());
				}
			}

			list.clear();
			remove(wave);
		}
	}

	/**
	 * Removes all resources for all objects that have been registered prior
	 * to this last call using the method {@link #cleanupMarkedObjects()},
	 * but delays the cleanup process. It basically moves the objects of
	 * interest into quarantine.
	 */
	public synchronized void quarantineAndCleanupMarkedObjects() {
		final RDKitCleanupTracker quarantineRDKitObjects = new RDKitCleanupTracker(
				this);
		clear();

		if (!quarantineRDKitObjects.isEmpty()) {
			// Create the future cleanup task
			final TimerTask futureCleanupTask = new TimerTask() {

				/**
				 * Cleans up all marked objects, which are put into
				 * quarantine for now.
				 */
				@Override
				public void run() {
					quarantineRDKitObjects.cleanupMarkedObjects();
				}
			};

			// Schedule the cleanup task for later
			final Timer timer = new Timer("Quarantine RDKit Object Cleanup",
					false);
			timer.schedule(futureCleanupTask,
					RDKit.RDKIT_OBJECT_CLEANUP_DELAY_FOR_QUARANTINE);
		}
	}
}