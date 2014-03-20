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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.RDKit.GenericRDKitException;
import org.RDKit.Int_Vect;
import org.RDKit.ROMol;
import org.rdkit.lucene.util.SystemUtils;

public class RDKit {

	//
	// Constants
	//

	/** The logger instance. */
	private static Logger LOGGER = Logger.getLogger(RDKit.class.getName());

	/**
	 * Time in milliseconds we will wait until we cleanup RDKit Objects, which
	 * are marked for delayed cleanup.
	 */
	public static final long RDKIT_OBJECT_CLEANUP_DELAY_FOR_QUARANTINE = 60000; // 60 seconds

	private static final String OS_WIN32 = "win32";

	private static final String OS_LINUX = "linux";

	private static final String OS_MACOSX = "macosx";

	private static final String ARCH_X86 = "x86";

	private static final String ARCH_X86_64 = "x86_64";

	/** List of libraries to be loaded for different operating systems (lib order is important). */
	private static final Map<String, String[]> LIBRARIES = new HashMap<String, String[]>();

	/** We define here what libraries are necessary to run the RDKit for the different supported platforms. */
	static {
		LIBRARIES.put(OS_WIN32 + "." + ARCH_X86, new String[] { "boost_system-vc100-mt-1_51.dll", "GraphMolWrap.dll" });
		LIBRARIES.put(OS_WIN32 + "." +  ARCH_X86_64, new String[] { "boost_system-vc100-mt-1_51.dll", "GraphMolWrap.dll" });
		LIBRARIES.put(OS_LINUX + "." +  ARCH_X86, new String[] { "libGraphMolWrap.so" });
		LIBRARIES.put(OS_LINUX + "." +  ARCH_X86_64, new String[] { "libGraphMolWrap.so" });
		LIBRARIES.put(OS_MACOSX + "." +  ARCH_X86_64, new String[] { "libGraphMolWrap.jnilib" });
	}

	/** The one and only cleaner instance. */
	private static final Cleaner CLEANER = new Cleaner();

	/** Flag to determine, if an activation was successful. */
	private static boolean g_bActivated = false;

	/** Flag to determine, if an activation was already performed. */
	private static boolean g_bActivationRan = false;

	//
	// Constructor
	//

	private RDKit() {
		// Only here to avoid instantiation of this utility class
	}

	//
	// Static Public Methods
	//

	/**
	 * Activates the RDKit to be used from a Java Application. This activation
	 * call gets only executed once. If it fails there is no recovery possible
	 * for the current Java VM. Subsequent calls to this method result in the
	 * same boolean result as the first call. The native libraries are expected
	 * to be found in lib/native/os/<OS Dependent Path>. This path gets resolved
	 * to an absolute path by the Java VM (File class).
	 * 
	 * @return True, if activation was successful. False otherwise.
	 */
	public static synchronized boolean activate() {
		return activate(null);
	}

	/**
	 * Activates the RDKit to be used from a Java Application. This activation
	 * call gets only executed once. If it fails there is no recovery possible
	 * for the current Java VM. Subsequent calls to this method result in the
	 * same boolean result as the first call.
	 * 
	 * @param strPath
	 *            An absolute or relative path to a directory, which contains OS
	 *            dependent sub directories win32, macosx and linux, which again
	 *            contain OS architecture dependent sub directories x86 and
	 *            x86_64. If the path is relative it gets resolved to an
	 *            absolute path by the Java VM (File class).
	 * 
	 * 
	 * @return True, if activation was successful. False otherwise.
	 */
	public static synchronized boolean activate(final String strPath) {
		if (!g_bActivationRan) {
			g_bActivationRan = true;

			try {
				String strRelativePath = (strPath == null ? "lib/native/os/"
						: strPath);

				// Determine operating system
				String strOS;
				if (SystemUtils.isWindows()) {
					strOS = OS_WIN32;
				}
				else if (SystemUtils.isMac()) {
					strOS = OS_MACOSX;
				}
				else if (SystemUtils.isUnix()) {
					strOS = OS_LINUX;
				}
				else {
					throw new UnsatisfiedLinkError(
							"Operating system is not supported from RDKit Native Libraries.");
				}

				// Determine OS architecture (32 or 64 bit)
				String strArch;
				if (System.getProperty("os.arch").indexOf("64") != -1) {
					strArch = ARCH_X86_64;

				}
				else {
					strArch = ARCH_X86;
				}

				strRelativePath += strOS + "/" + strArch + "/";
				final String[] arrLibraries = LIBRARIES.get(strOS + "." + strArch);

				if (arrLibraries == null) {
					throw new UnsatisfiedLinkError("Unsupported operating system or architecture.");
				}
				else {
					// Load libraries
					for (final String strLibName : arrLibraries) {
						System.load(new File(strRelativePath, strLibName).getAbsolutePath());
					}
				}

				g_bActivated = true;
			}
			catch (final UnsatisfiedLinkError e) {
				LOGGER.log(Level.SEVERE, "Unable to load RDKit Native Libraries.", e);
			}
		}

		return g_bActivated;
	}

	/**
	 * Creates a byte array from an RDKit molecule to save it in binary form.
	 * 
	 * @param mol RDkit molecule to be serialized.
	 * 
	 * @return Byte array.
	 * 
	 * @throws GenericRDKitException Thrown, if serialization fails.
	 */
	public static byte[] toByteArray(final ROMol mol)
			throws GenericRDKitException {
		final Int_Vect iv = mol.ToBinary();
		final byte[] bytes = new byte[(int)iv.size()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte)iv.get(i);
		}
		return bytes;
	}

	/**
	 * Creates an RDKit molecule from a byte array.
	 * 
	 * @param bytes Byte representing a binary RDKit molecule to be deserialized.
	 * 
	 * @return RDKit molecule.
	 * 
	 * @throws GenericRDKitException Thrown, if deserialization fails.
	 */
	public static ROMol toROMol(final byte[] bytes)
			throws GenericRDKitException {
		final Int_Vect iv = new Int_Vect(bytes.length);
		for (int i = 0; i < bytes.length; i++) {
			iv.set(i, bytes[i]);
		}
		return ROMol.MolFromBinary(iv);
	}

	/**
	 * Creates a new wave id. This id must be unique in the context of the
	 * overall runtime of the Java VM, at least in the context of the same class
	 * loader and memory area.
	 * 
	 * @return Unique wave id.
	 */
	public static int createUniqueCleanupWaveId() {
		return CLEANER.createUniqueCleanupWaveId();
	}

	/**
	 * Registers an RDKit based object, which must have a delete() method
	 * implemented for freeing up resources later. The cleanup will happen for
	 * all registered objects when the method {@link #cleanupMarkedObjects()} is
	 * called. Note: If the same rdkitObject was already registered for a wave
	 * it would be cleaned up multiple times, which may have negative side
	 * effects and may even cause errors. Hence, always mark an object only once
	 * for cleanup, or - if required in certain situations - call
	 * {@link #markForCleanup(Object, boolean)} and set the last parameter to
	 * true to remove the object from the formerly registered wave.
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called to
	 *            free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when not
	 *            used anymore. Can be null.
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 * 
	 * @see #cleanupMarkedObjects(int)
	 */
	public static <T extends Object> T markForCleanup(final T rdkitObject) {
		return CLEANER.markForCleanup(rdkitObject);
	}

	/**
	 * Registers an RDKit based object, which must have a delete() method
	 * implemented for freeing up resources later. The cleanup will happen for
	 * all registered objects when the method {@link #cleanupMarkedObjects()} is
	 * called. Note: If the last parameter is set to true and the same
	 * rdkitObject was already registered for another wave it will be removed
	 * from the former wave list and will exist only in the wave specified here.
	 * This can be useful for instance, if an object is first marked as part of
	 * a wave and later on it is determined that it needs to live longer (e.g.
	 * without a wave). In this case the first time this method would be called
	 * with a wave id, the second time without wave id (which would internally
	 * be wave = 0).
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called to
	 *            free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when not
	 *            used anymore. Can be null.
	 * @param bRemoveFromOtherWave
	 *            Checks, if the object was registered before with another wave
	 *            id, and remove it from that former wave. Usually this should
	 *            be set to false for performance reasons.
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 * 
	 * @see #cleanupMarkedObjects(int)
	 */
	public static <T extends Object> T markForCleanup(final T rdkitObject,
			final boolean bRemoveFromOtherWave) {
		return CLEANER.markForCleanup(rdkitObject, bRemoveFromOtherWave);
	}

	/**
	 * Registers an RDKit based object that is used within a certain block
	 * (wave). $ This object must have a delete() method implemented for freeing
	 * up resources later. The cleanup will happen for all registered objects
	 * when the method {@link #cleanupMarkedObjects(int)} is called with the
	 * same wave. Note: If the same rdkitObject was already registered for
	 * another wave (or no wave) it would be cleaned up multiple times, which
	 * may have negative side effects and may even cause errors. Hence, always
	 * mark an object only once for cleanup, or - if required in certain
	 * situations - call {@link #markForCleanup(Object, int, boolean)} and set
	 * the last parameter to true to remove the object from the formerly
	 * registered wave.
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called to
	 *            free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when not
	 *            used anymore. Can be null.
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 */
	public static <T extends Object> T markForCleanup(final T rdkitObject,
			final int wave) {
		return CLEANER.markForCleanup(rdkitObject, wave);
	}

	/**
	 * Registers an RDKit based object that is used within a certain block
	 * (wave). $ This object must have a delete() method implemented for freeing
	 * up resources later. The cleanup will happen for all registered objects
	 * when the method {@link #cleanupMarkedObjects(int)} is called with the
	 * same wave. Note: If the last parameter is set to true and the same
	 * rdkitObject was already registered for another wave (or no wave) it will
	 * be removed from the former wave list and will exist only in the wave
	 * specified here. This can be useful for instance, if an object is first
	 * marked as part of a wave and later on it is determined that it needs to
	 * live longer (e.g. without a wave). In this case the first time this
	 * method would be called with a wave id, the second time without wave id
	 * (which would internally be wave = 0).
	 * 
	 * @param <T>
	 *            Any class that implements a delete() method to be called to
	 *            free up resources.
	 * @param rdkitObject
	 *            An RDKit related object that should free resources when not
	 *            used anymore. Can be null.
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 * @param bRemoveFromOtherWave
	 *            Checks, if the object was registered before with another wave
	 *            id, and remove it from that former wave. Usually this should
	 *            be set to false for performance reasons.
	 * 
	 * @return The same object that was passed in. Null, if null was passed in.
	 */
	public static <T extends Object> T markForCleanup(final T rdkitObject,
			final int wave, final boolean bRemoveFromOtherWave) {
		return CLEANER.markForCleanup(rdkitObject, wave, bRemoveFromOtherWave);
	}

	/**
	 * Frees resources for all objects that have been registered prior to this
	 * last call using the method {@link #cleanupMarkedObjects()}.
	 */
	public static void cleanupMarkedObjects() {
		CLEANER.cleanupMarkedObjects();
	}

	/**
	 * Frees resources for all objects that have been registered prior to this
	 * last call for a certain wave using the method
	 * {@link #cleanupMarkedObjects(int)}.
	 * 
	 * @param wave
	 *            A number that identifies objects registered for a certain
	 *            "wave".
	 */
	public static void cleanupMarkedObjects(final int wave) {
		CLEANER.cleanupMarkedObjects(wave);
	}

	/**
	 * Removes all resources for all objects that have been registered prior to
	 * this last call using the method {@link #cleanupMarkedObjects()}, but
	 * delayes the cleanup process. It basically moves the objects of interest
	 * into quarantine.
	 */
	public static void quarantineAndCleanupMarkedObjects() {
		CLEANER.quarantineAndCleanupMarkedObjects();
	}
}
