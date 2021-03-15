/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

package de.mpg.biochem.mars.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import de.mpg.biochem.mars.util.MarsUtil.ThrowingConsumer;

public class MarsUtil {
	
	public static void threadPoolBuilder(StatusService statusService,
			LogService logService, Runnable updateStatus, List<Runnable> tasks, int numThreads)
		{

			final AtomicBoolean progressUpdating = new AtomicBoolean(true);
			
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

			try {
				Thread progressThread = new Thread() {

					public synchronized void run() {
						try {
							while (progressUpdating.get()) {
								Thread.sleep(100);
								updateStatus.run();
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
				};

				progressThread.start();
				
				tasks.forEach(task -> threadPool.submit(task));
				threadPool.shutdown();
				threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

				progressUpdating.set(false);

			}
			catch (InterruptedException e) {
				// handle exceptions
				e.printStackTrace();
				logService.info(LogBuilder.endBlock(false));
			}
			finally {
				statusService.showProgress(100, 100);
				statusService.showStatus("Done!");
			}
		}
/*
	public static void forkJoinPoolBuilder(StatusService statusService,
		LogService logService, Runnable updateStatus, Runnable task, int numThreads)
	{

		final AtomicBoolean progressUpdating = new AtomicBoolean(true);

		ForkJoinPool forkJoinPool = new ForkJoinPool(numThreads);
		try {
			Thread progressThread = new Thread() {

				public synchronized void run() {
					try {
						while (progressUpdating.get()) {
							Thread.sleep(100);
							updateStatus.run();
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			};

			progressThread.start();

			forkJoinPool.submit(task).get();

			progressUpdating.set(false);

		}
		catch (InterruptedException | ExecutionException e) {
			// handle exceptions
			e.printStackTrace();
			logService.info(LogBuilder.endBlock(false));
		}
		finally {
			forkJoinPool.shutdown();
			statusService.showProgress(100, 100);
			statusService.showStatus("Done!");
		}
	}
*/
	/**
	 * Return Json String in pretty print format.
	 * 
	 * @param throwingConsumer Consumer to generate JSON.
	 * @return Json string.
	 */
	public static String dumpJSON(
		ThrowingConsumer<JsonGenerator, IOException> throwingConsumer)
	{
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		// Create a new jfactory.
		JsonFactory jfactory = new JsonFactory();

		JsonGenerator jGenerator;
		try {
			jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
			jGenerator.useDefaultPrettyPrinter();
			throwingConsumer.accept(jGenerator);
			jGenerator.close();
			String output = stream.toString();
			stream.close();

			return output;
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void writeJsonRecord(JsonConvertibleRecord record, File file,
		JsonFactory jfactory) throws IOException
	{
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		record.toJSON(jGenerator);
		jGenerator.close();
		stream.flush();
		stream.close();
	}

	/**
	 * Used to bypass unknown Json objects with JacksonJson streaming interface.
	 * This will also pass through all arrays contained within the objects.
	 * 
	 * @param jParser JsonParser stream to processing pass through for.
	 * @throws IOException Thrown if unable to parse Json from the JsonParser
	 *           given.
	 */
	public static void passThroughUnknownObjects(JsonParser jParser)
		throws IOException
	{
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			if (jParser.getCurrentToken() == JsonToken.START_OBJECT)
				passThroughUnknownObjects(jParser);
		}
	}

	/**
	 * Used to bypass unknown Json arrays with JacksonJson streaming interface.
	 * This will also pass over all objects contained inside the arrays.
	 * 
	 * @param jParser JsonParser stream to processing pass through for.
	 * @throws IOException Thrown if unable to parse Json from the JsonParser
	 *           given.
	 */
	public static void passThroughUnknownArrays(JsonParser jParser)
		throws IOException
	{
		while (jParser.nextToken() != JsonToken.END_ARRAY) {
			if (jParser.getCurrentToken() == JsonToken.START_ARRAY)
				passThroughUnknownArrays(jParser);
		}
	}

	public static <T, E extends Exception> Predicate<T> catchConsumerException(
		ThrowingConsumer<T, E> throwingConsumer, Class<E> exceptionType)
	{

		return i -> {
			try {
				throwingConsumer.accept(i);
				return true;
			}
			catch (Exception e) {
				try {
					E ex = exceptionType.cast(e);
					System.err.println("Exception: " + ex.getMessage());
				}
				catch (ClassCastException cex) {
					throw new RuntimeException(e);
				}
				return false;
			}
		};
	}

	@FunctionalInterface
	public interface ThrowingConsumer<T, E extends Exception> {

		void accept(T t) throws E;
	}
}
