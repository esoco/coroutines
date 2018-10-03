//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'coroutines' project.
// Copyright 2018 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
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
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Suspending;
import de.esoco.coroutine.Suspension;

import java.io.IOException;

import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;
import org.obrel.type.MetaTypes;

import static org.obrel.core.RelationTypes.newType;


/********************************************************************
 * The base class for coroutine steps that perform communication through
 * instances of {@link AsynchronousSocketChannel}.
 *
 * @author eso
 */
public abstract class AsynchronousSocketStep extends AsynchronousChannelStep
	implements Suspending
{
	//~ Static fields/initializers ---------------------------------------------

	/**
	 * State: an {@link AsynchronousSocketChannel} that has been openened and
	 * connected by an asynchronous execution.
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
	 * Creates a new instance that connects to the socket the address of which
	 * is provided by the given factory. The factory may return NULL if the step
	 * is intended to connect to a channel that has been connected by a previous
	 * coroutine step.
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
	public Suspension<ByteBuffer> runAsync(
		CompletableFuture<ByteBuffer> fPreviousExecution,
		CoroutineStep<ByteBuffer, ?>  rNextStep,
		Continuation<?>				  rContinuation)
	{
		Suspension<ByteBuffer> rSuspension =
			rContinuation.suspend(this, rNextStep);

		fPreviousExecution.thenAcceptAsync(
			b -> connectAsync(b, rSuspension),
			rContinuation);

		return rSuspension;
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
	 * Must be implemented for the blocking execution of a step. It still
	 * receives an {@link AsynchronousSocketChannel} which must be accessed with
	 * the blocking API (like {@link Future#get()}).
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
			AsynchronousSocketChannel aChannel = getChannel(rContinuation);

			if (aChannel.getRemoteAddress() == null)
			{
				aChannel.connect(getSocketAddress(rContinuation)).get();
			}

			performBlockingOperation(aChannel, rData);
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
	 * existing {@link #SOCKET_CHANNEL} relation. If that doesn't exists or if
	 * it contains a closed channel a new {@link AsynchronousSocketChannel} will
	 * be opened and stored in the state object.
	 *
	 * @param  rContinuation The continuation to query for an existing channel
	 *
	 * @return The channel
	 *
	 * @throws IOException If opening the channel fails
	 */
	protected AsynchronousSocketChannel getChannel(
		Continuation<?> rContinuation) throws IOException
	{
		Coroutine<?, ?> rCoroutine = rContinuation.getCurrentCoroutine();

		AsynchronousSocketChannel rChannel = rCoroutine.get(SOCKET_CHANNEL);

		if (rChannel == null || !rChannel.isOpen())
		{
			rChannel = AsynchronousSocketChannel.open();
			rCoroutine.set(SOCKET_CHANNEL, rChannel)
					  .annotate(MetaTypes.MANAGED);
		}

		return rChannel;
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
	 * Opens and connects a {@link Channel} to the {@link SocketAddress} of this
	 * step and then performs the channel operation asynchronously.
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
				getChannel(rSuspension.continuation());

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
			rSuspension.continuation().fail(e);
		}
	}
}
