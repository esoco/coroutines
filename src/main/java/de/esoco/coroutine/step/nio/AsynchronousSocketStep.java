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

import java.io.IOException;

import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;
import org.obrel.type.MetaTypes;

import static org.obrel.core.RelationTypes.newType;
import static org.obrel.type.MetaTypes.MANAGED;


/********************************************************************
 * The base class for coroutine steps that perform communication on instances of
 * {@link AsynchronousSocketChannel}. The channel will be opened and connected
 * as necessary. It may also be provided before the step is invoked in a state
 * relation with the type {@link #SOCKET_CHANNEL}. If the channel is opened by
 * this step it will have the {@link MetaTypes#MANAGED} flag so that it will be
 * automatically closed when the {@link CoroutineScope} finishes.
 *
 * @author eso
 */
public abstract class AsynchronousSocketStep
	extends AsynchronousChannelStep<ByteBuffer, ByteBuffer>
{
	//~ Static fields/initializers ---------------------------------------------

	/**
	 * State: the {@link AsynchronousSocketChannel} that the steps in a
	 * coroutine operate on.
	 */
	public static final RelationType<AsynchronousSocketChannel> SOCKET_CHANNEL =
		newType();

	static
	{
		RelationTypes.init(AsynchronousSocketStep.class);
	}

	//~ Instance fields --------------------------------------------------------

	private final Function<Continuation<?>, SocketAddress> fGetSocketAddress;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that connects to the socket with the address
	 * provided by the given factory. The factory may return NULL if the step
	 * should connect to a channel that is stored in a state relation with the
	 * type {@link #SOCKET_CHANNEL}.
	 *
	 * @param fGetSocketAddress A function that provides the socket address to
	 *                          connect to from the current continuation
	 */
	public AsynchronousSocketStep(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress)
	{
		Objects.requireNonNull(fGetSocketAddress);

		this.fGetSocketAddress = fGetSocketAddress;
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
			b -> connectAsync(b, rContinuation.suspend(this, rNextStep)));
	}

	/***************************************
	 * Implementation of the ChannelOperation functional interface method
	 * signature.
	 *
	 * @see AsynchronousChannelStep.ChannelOperation#execute(int,java.nio.channels.AsynchronousChannel,
	 *      ByteBuffer, AsynchronousChannelStep.ChannelCallback)
	 */
	protected abstract boolean performAsyncOperation(
		int													nBytesProcessed,
		AsynchronousSocketChannel							rChannel,
		ByteBuffer											rData,
		ChannelCallback<Integer, AsynchronousSocketChannel> rCallback)
		throws Exception;

	/***************************************
	 * Must be implemented for the blocking execution of a step. It receives an
	 * {@link AsynchronousSocketChannel} which must be accessed through the
	 * blocking API (like {@link Future#get()}).
	 *
	 * @param  aChannel The channel to perform the operation on
	 * @param  rData    The byte buffer for the operation data
	 *
	 * @throws Exception Any kind of exception may be thrown
	 */
	protected abstract void performBlockingOperation(
		AsynchronousSocketChannel aChannel,
		ByteBuffer				  rData) throws Exception;

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
			AsynchronousSocketChannel rChannel =
				getSocketChannel(rContinuation);

			if (rChannel.getRemoteAddress() == null)
			{
				rChannel.connect(getSocketAddress(rContinuation)).get();
			}

			performBlockingOperation(rChannel, rData);
		}
		catch (Exception e)
		{
			throw new CoroutineException(e);
		}

		return rData;
	}

	/***************************************
	 * Returns the address of the socket to connect to.
	 *
	 * @param  rContinuation The current continuation
	 *
	 * @return The socket address
	 */
	protected SocketAddress getSocketAddress(Continuation<?> rContinuation)
	{
		return fGetSocketAddress.apply(rContinuation);
	}

	/***************************************
	 * Returns the socket address factory of this step.
	 *
	 * @return The socket address factory function
	 */
	protected Function<Continuation<?>, SocketAddress> getSocketAddressFactory()
	{
		return fGetSocketAddress;
	}

	/***************************************
	 * Returns the channel to be used by this step. This first checks the
	 * currently exexcuting coroutine in the continuation parameter for an
	 * existing {@link #SOCKET_CHANNEL} relation. If that doesn't exists or the
	 * channel is closed a new {@link AsynchronousSocketChannel} will be opened
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
	protected AsynchronousSocketChannel getSocketChannel(
		Continuation<?> rContinuation) throws IOException
	{
		Coroutine<?, ?> rCoroutine = rContinuation.getCurrentCoroutine();

		AsynchronousSocketChannel rChannel = rCoroutine.get(SOCKET_CHANNEL);

		if (rChannel == null || !rChannel.isOpen())
		{
			rChannel =
				AsynchronousSocketChannel.open(getChannelGroup(rContinuation));
			rCoroutine.set(SOCKET_CHANNEL, rChannel).annotate(MANAGED);
		}

		return rChannel;
	}

	/***************************************
	 * Opens and connects a {@link AsynchronousSocketChannel} to the {@link
	 * SocketAddress} of this step and then performs the channel operation
	 * asynchronously.
	 *
	 * @param rData       The byte buffer of the data to be processed
	 * @param rSuspension The coroutine suspension to be resumed when the
	 *                    operation is complete
	 */
	private void connectAsync(
		ByteBuffer			   rData,
		Suspension<ByteBuffer> rSuspension)
	{
		try
		{
			AsynchronousSocketChannel rChannel =
				getSocketChannel(rSuspension.continuation());

			if (rChannel.getRemoteAddress() == null)
			{
				SocketAddress rSocketAddress =
					fGetSocketAddress.apply(rSuspension.continuation());

				rChannel.connect(
					rSocketAddress,
					rData,
					new ChannelCallback<>(
						rChannel,
						rSuspension,
						this::performAsyncOperation));
			}
			else
			{
				performAsyncOperation(
					FIRST_OPERATION,
					rChannel,
					rData,
					new ChannelCallback<>(
						rChannel,
						rSuspension,
						this::performAsyncOperation));
			}
		}
		catch (Exception e)
		{
			rSuspension.fail(e);
		}
	}
}
