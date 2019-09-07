//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'coroutines' project.
// Copyright 2019 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
package de.esoco.coroutine.step.nio;

import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineException;
import de.esoco.coroutine.CoroutineScope;
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Suspension;

import de.esoco.lib.collection.CollectionUtil;

import java.io.File;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;
import org.obrel.type.MetaTypes;

import static org.obrel.core.RelationTypes.newLongType;
import static org.obrel.core.RelationTypes.newType;
import static org.obrel.type.MetaTypes.MANAGED;


/********************************************************************
 * The base class for coroutine steps that perform communication on instances of
 * {@link AsynchronousFileChannel}. The channel will be opened and connected as
 * necessary. It may also be provided before the step is invoked in a state
 * relation with the type {@link #FILE_CHANNEL}. If the channel is opened by
 * this step it will have the {@link MetaTypes#MANAGED} flag so that it will be
 * automatically closed when the {@link CoroutineScope} finishes.
 *
 * @author eso
 */
public abstract class AsynchronousFileStep
	extends AsynchronousChannelStep<ByteBuffer, ByteBuffer>
{
	//~ Static fields/initializers ---------------------------------------------

	/**
	 * State: the {@link AsynchronousFileChannel} that the steps in a coroutine
	 * operate on.
	 */
	public static final RelationType<AsynchronousFileChannel> FILE_CHANNEL =
		newType();

	/**
	 * The current position when reading from or writing to a file channel. Can
	 * also be set in advance to start reading from that position instead of the
	 * file start.
	 */
	public static final RelationType<Long> FILE_POSITION = newLongType();

	static
	{
		RelationTypes.init(AsynchronousFileStep.class);
	}

	//~ Instance fields --------------------------------------------------------

	private final Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that opens a file channel with the given factory.
	 * The factory may return NULL if the step should use a file channel that is
	 * stored in a state relation with the type {@link #FILE_CHANNEL}.
	 *
	 * @param fGetFileChannel A function that opens a file channel for the
	 *                        current continuation
	 */
	public AsynchronousFileStep(
		Function<Continuation<?>, AsynchronousFileChannel> fGetFileChannel)
	{
		Objects.requireNonNull(fGetFileChannel);

		this.fGetFileChannel = fGetFileChannel;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * A helper function that opens a file channel for a certain file name and
	 * open options.
	 *
	 * @param  sFileName     The file name
	 * @param  rMode         The open option for the file access mode (e.g.
	 *                       READ, WRITE)
	 * @param  rExtraOptions Optional extra file open options
	 *
	 * @return The file channel
	 */
	protected static AsynchronousFileChannel openFileChannel(
		String		  sFileName,
		OpenOption    rMode,
		OpenOption... rExtraOptions)
	{
		try
		{
			return AsynchronousFileChannel.open(
				new File(sFileName).toPath(),
				CollectionUtil.join(rExtraOptions, rMode));
		}
		catch (IOException e)
		{
			throw new CoroutineException(e);
		}
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<ByteBuffer> fPreviousExecution,
						 CoroutineStep<ByteBuffer, ?>  rNextStep,
						 Continuation<?>			   rContinuation)
	{
		rContinuation.continueAccept(
			fPreviousExecution,
			b -> transferAsync(b, rContinuation.suspend(this, rNextStep)));
	}

	/***************************************
	 * Implementation of the ChannelOperation functional interface method
	 * signature.
	 *
	 * @see AsynchronousChannelStep.ChannelOperation#execute(int,java.nio.channels.AsynchronousChannel,
	 *      ByteBuffer, AsynchronousChannelStep.ChannelCallback)
	 */
	protected abstract boolean performAsyncOperation(
		int												  nBytesProcessed,
		AsynchronousFileChannel							  rChannel,
		ByteBuffer										  rData,
		ChannelCallback<Integer, AsynchronousFileChannel> rCallback)
		throws Exception;

	/***************************************
	 * Must be implemented for the blocking execution of a step. It receives an
	 * {@link AsynchronousFileChannel} which must be accessed through the
	 * blocking API (like {@link Future#get()}).
	 *
	 * @param  aChannel The channel to perform the operation on
	 * @param  rData    The byte buffer for the operation data
	 *
	 * @throws Exception Any kind of exception may be thrown
	 */
	protected abstract void performBlockingOperation(
		AsynchronousFileChannel aChannel,
		ByteBuffer				rData) throws Exception;

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected ByteBuffer execute(
		ByteBuffer		rData,
		Continuation<?> rContinuation)
	{
		try
		{
			AsynchronousFileChannel rChannel = getFileChannel(rContinuation);

			performBlockingOperation(rChannel, rData);
		}
		catch (Exception e)
		{
			throw new CoroutineException(e);
		}

		return rData;
	}

	/***************************************
	 * Returns the channel to be used by this step. This first checks the
	 * currently exexcuting coroutine in the continuation parameter for an
	 * existing {@link #FILE_CHANNEL} relation. If that doesn't exists or the
	 * channel is closed a new {@link AsynchronousFileChannel} will be opened
	 * and stored in the coroutine relation. Using the coroutine to store the
	 * channel allows coroutines to be structured so that multiple subroutines
	 * perform communication on different channels.
	 *
	 * @param  rContinuation The continuation to query for an existing channel
	 *
	 * @return The socket channel
	 *
	 * @throws IOException If opening the channel fails
	 */
	protected AsynchronousFileChannel getFileChannel(
		Continuation<?> rContinuation) throws IOException
	{
		Coroutine<?, ?> rCoroutine = rContinuation.getCurrentCoroutine();

		AsynchronousFileChannel rChannel = rCoroutine.get(FILE_CHANNEL);

		if (rChannel == null || !rChannel.isOpen())
		{
			rChannel = fGetFileChannel.apply(rContinuation);
			rCoroutine.set(FILE_CHANNEL, rChannel).annotate(MANAGED);
		}

		return rChannel;
	}

	/***************************************
	 * Returns the file channel factory of this step.
	 *
	 * @return The file channel factory function
	 */
	protected Function<Continuation<?>, AsynchronousFileChannel>
	getFileChannelFactory()
	{
		return fGetFileChannel;
	}

	/***************************************
	 * Opens a {@link AsynchronousFileChannel} and then performs the channel
	 * operation asynchronously.
	 *
	 * @param rData       The byte buffer of the data to be processed
	 * @param rSuspension The coroutine suspension to be resumed when the
	 *                    operation is complete
	 */
	private void transferAsync(
		ByteBuffer			   rData,
		Suspension<ByteBuffer> rSuspension)
	{
		try
		{
			AsynchronousFileChannel rChannel =
				getFileChannel(rSuspension.continuation());

			performAsyncOperation(
				FIRST_OPERATION,
				rChannel,
				rData,
				new ChannelCallback<>(
					rChannel,
					rSuspension,
					this::performAsyncOperation));
		}
		catch (Exception e)
		{
			rSuspension.fail(e);
		}
	}
}
