/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.molecule;

/**
 * This interface must be implemented by Mars GUIs that provide MoleculeArchive
 * windows with access to archive information. mars-swing and mars-fx both
 * contain concrete implementations of this interface.
 * <p>
 * This provides direct access to the MoleculeArchive window that is currently
 * displaying the archive contents using the {@link MoleculeArchive#getWindow()
 * getWindow} method. Using this interface the window can be updated, locked,
 * unlocked, closed, Log Messages can be printed to the lock mode background and
 * the Progress of a running job can be provided.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public interface MoleculeArchiveWindow {

	/**
	 * Force the window to refresh its components. Deprecated - lock/unlock could
	 * also be used to force a reset or type casting if needed.
	 */
	@Deprecated
	public void update();

	/**
	 * This is used to report the progress for a long running task working on the
	 * archive when its locked. This should be a value between 0 and 1 with 1
	 * representing completion.
	 * 
	 * @param progress Fraction of job that is complete.
	 */
	public void setProgress(double progress);

	/**
	 * Update the message presented on the lock screen.
	 * 
	 * @param message Message presented on the lock screen.
	 */
	public void updateLockMessage(String message);

	/**
	 * Add a message to the log that is presented in the background of the lock
	 * screen. All log messages sent to the method
	 * {@link MoleculeArchive#logln(String message) logln(String message)} are
	 * printed to the lock screen background using this method.
	 * 
	 * @param message String message to add to the lock screen log background.
	 */
	@Deprecated
	public void addLogMessage(String message);

	/**
	 * Add a message to the log that is presented in the background of the lock
	 * screen. All log messages sent to the method
	 * {@link MoleculeArchive#logln(String message) addLogMessage(String message)}
	 * are printed to the lock screen background using this method.
	 * 
	 * @param message String message to add to the lock screen log background.
	 */
	public void log(String message);

	/**
	 * Add a message and start a new line in the log that is presented in the
	 * background of the lock screen. All log messages sent to the method
	 * {@link MoleculeArchive#logln(String message) addLogMessage(String message)}
	 * are printed to the lock screen background using this method.
	 * 
	 * @param message String message to add to the lock screen log background.
	 */
	public void logln(String message);

	/**
	 * Lock the MoleculeArchive window to prevent changes from being made at the
	 * same time as calculations are running on the archive. Allow for setting a
	 * specific message in the lock screen such as the calculation that is
	 * currently running.
	 * 
	 * @param message Message to present in the lock screen.
	 */
	public void lock(String message);

	/**
	 * Lock the MoleculeArchive window to prevent changes from being made at the
	 * same time as calculations are running on the archive.
	 */
	public void lock();

	/**
	 * Unlock the MoleculeArchive window once processing is complete.
	 */
	public void unlock();

	/**
	 * Close the archive window. Deprecated - archive windows should be closed
	 * directly. If this is required typecasting could be used.
	 */
	@Deprecated
	public void close();
}
