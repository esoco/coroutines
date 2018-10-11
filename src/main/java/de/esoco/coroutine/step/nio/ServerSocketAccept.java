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
import de.esoco.coroutine.Suspension;
import de.esoco.coroutine.step.Loop;

import java.io.IOException;

import java.net.SocketAddress;

import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;
import org.obrel.type.MetaTypes;

import static org.obrel.core.RelationTypes.newType;


/********************************************************************
 * A coroutine step for servers that listens to network request through an
 * instance of {@link AsynchronousServerSocketChannel}. The step will suspend
 * execution until a request is accepted and then spawn another coroutine for
 * the handling of the request. The handling coroutine will receive the {@link
 * AsynchronousSocketChannel} for the communication with the client. It could
 * then store the channel as {@link AsynchronousSocketStep#SOCKET_CHANNEL} and
 * process the request with {@link SocketReceive} and {@link SocketSend} steps.
 *
 * <p>After the request handling coroutine has been spawned this step will
 * resume execution of it's own coroutine. A typical server accept loop
 * therefore needs to be implemented in that coroutine, e.g. inside a
 * conditional {@link Loop} step. Handling coroutines will run in parallel in
 * separate continuations but in the same scope. Interdependencies between the
 * main coroutine and the request handling should be avoided to prevent the risk
 * of deadlocks.</p>
 *
 * @author eso
 */
public class ServerSocketAccept extends AsynchronousChannelStep<Void, Void>
{
	//~ Static fields/initializers ---------------------------------------------

	/**
	 * State: an {@link AsynchronousServerSocketChannel} that has been openened
	 * and connected by an asynchronous execution.
	 */
	public static final RelationType<AsynchronousServerSocketChannel> SERVER_SOCKET_CHANNEL =
		newType();

	static
	{
		RelationTypes.init(ServerSocketAccept.class);
	}

	//~ Instance fields --------------------------------------------------------

	private final Function<Continuation<?>, SocketAddress> fGetSocketAddress;
	private Coroutine<AsynchronousSocketChannel, ?>		   rRequestHandler;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that accepts a single server request and processes
	 * it asynchronously in a coroutine. The server socket is bound to the local
	 * socket with the address provided by the given factory. The factory may
	 * return NULL if the step should connect to a channel that is stored in a
	 * state relation with the type {@link #SERVER_SOCKET_CHANNEL}.
	 *
	 * @param fGetSocketAddress A function that provides the socket address to
	 *                          connect to from the current continuation
	 * @param rRequestHandler   A coroutine that processes a single server
	 *                          request
	 */
	public ServerSocketAccept(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress,
		Coroutine<AsynchronousSocketChannel, ?>  rRequestHandler)
	{
		Objects.requireNonNull(fGetSocketAddress);

		this.fGetSocketAddress = fGetSocketAddress;
		this.rRequestHandler   = rRequestHandler;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Accepts an incoming request on the given socket address and then handles
	 * it by executing the given coroutine with the client socket channel as
	 * it's input.
	 *
	 * @param  fGetSocketAddress A function that produces the address of the
	 *                           local address to accept the request from
	 * @param  rRequestHandler   The coroutine to process the next request with
	 *
	 * @return The new step
	 */
	public static ServerSocketAccept acceptRequestOn(
		Function<Continuation<?>, SocketAddress> fGetSocketAddress,
		Coroutine<AsynchronousSocketChannel, ?>  rRequestHandler)
	{
		return new ServerSocketAccept(fGetSocketAddress, rRequestHandler);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<Void> fPreviousExecution,
						 CoroutineStep<Void, ?>  rNextStep,
						 Continuation<?>		 rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
			v -> acceptAsync(rContinuation.suspend(this, rNextStep)),
			rContinuation);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected Void execute(Void rData, Continuation<?> rContinuation)
	{
		try
		{
			AsynchronousServerSocketChannel rChannel =
				getServerSocketChannel(rContinuation);

			rContinuation.scope()
						 .blocking(rRequestHandler, rChannel.accept().get());
		}
		catch (Exception e)
		{
			throw new CoroutineException(e);
		}

		return null;
	}

	/***************************************
	 * Returns the channel to be used by this step. This first checks the
	 * currently exexcuting coroutine in the continuation parameter for an
	 * existing {@link #SERVER_SOCKET_CHANNEL} relation. If that doesn't exists
	 * or if it contains a closed channel a new {@link
	 * AsynchronousServerSocketChannel} will be opened and stored in the state
	 * object.
	 *
	 * @param  rContinuation The continuation to query for an existing channel
	 *
	 * @return The channel
	 *
	 * @throws IOException If opening the channel fails
	 */
	protected AsynchronousServerSocketChannel getServerSocketChannel(
		Continuation<?> rContinuation) throws IOException
	{
		Coroutine<?, ?> rCoroutine = rContinuation.getCurrentCoroutine();

		AsynchronousServerSocketChannel rChannel =
			rCoroutine.get(SERVER_SOCKET_CHANNEL);

		if (rChannel == null || !rChannel.isOpen())
		{
			rChannel = AsynchronousServerSocketChannel.open();
			rCoroutine.set(SERVER_SOCKET_CHANNEL, rChannel)
					  .annotate(MetaTypes.MANAGED);
		}

		if (rChannel.getLocalAddress() == null)
		{
			rChannel.bind(getSocketAddress(rContinuation));
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
	 * @param rSuspension The coroutine suspension to be resumed when the
	 *                    operation is complete
	 */
	private void acceptAsync(Suspension<Void> rSuspension)
	{
		try
		{
			AsynchronousServerSocketChannel rChannel =
				getServerSocketChannel(rSuspension.continuation());

			rChannel.accept(
				null,
				new AcceptCallback(rRequestHandler, rSuspension));
		}
		catch (Exception e)
		{
			rSuspension.fail(e);
		}
	}

	//~ Inner Classes ----------------------------------------------------------

	/********************************************************************
	 * A {@link CompletionHandler} implementation that receives the result of an
	 * asynchronous accept and processes the request with an asynchronous
	 * coroutine execution.
	 *
	 * @author eso
	 */
	protected static class AcceptCallback
		implements CompletionHandler<AsynchronousSocketChannel, Void>
	{
		//~ Instance fields ----------------------------------------------------

		private Coroutine<AsynchronousSocketChannel, ?> rRequestHandler;
		private final Suspension<Void>				    rSuspension;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance.
		 *
		 * @param rRequestHandler The coroutine to process the request with
		 * @param rSuspension     The suspension to resume after accepting
		 */
		public AcceptCallback(
			Coroutine<AsynchronousSocketChannel, ?> rRequestHandler,
			Suspension<Void>						rSuspension)
		{
			this.rRequestHandler = rRequestHandler;
			this.rSuspension     = rSuspension;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public void completed(
			AsynchronousSocketChannel rRequestChannel,
			Void					  rIgnored)
		{
			rSuspension.continuation()
					   .scope()
					   .async(rRequestHandler, rRequestChannel);

			rSuspension.resume();
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public void failed(Throwable eError, Void rIgnored)
		{
			rSuspension.fail(eError);
		}
	}
}
