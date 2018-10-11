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
import de.esoco.coroutine.CoroutineContext;
import de.esoco.coroutine.CoroutineException;
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Suspension;

import java.io.IOException;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.obrel.core.RelationType;
import org.obrel.core.RelationTypes;

import static org.obrel.core.RelationTypes.newType;


/********************************************************************
 * The base class for coroutine steps that perform communication through
 * instances of {@link AsynchronousChannel}. It contains the inner class {@link
 * ChannelCallback} that implements most of the {@link CompletionHandler}
 * interface needed for asynchonous channel communication. The actual channel
 * operation must be provided to it as an implementation of the function
 * interface {@link ChannelOperation}.
 *
 * <p>To simplify the generic declaration of subclasses both input and output
 * type are declared as {@link ByteBuffer}, where the returned value will be the
 * input value. Input buffers must be provided by the preceding step in a
 * coroutine and initialize it for the respective channel step implemenation.
 * For a reading step this means that the buffer must have an adequate capacity.
 * For a writing step it must contain the data to write and it must have been
 * flipped if necessary (see {@link Buffer#flip()} for details).</p>
 *
 * @author eso
 */
public abstract class AsynchronousChannelStep<I, O> extends CoroutineStep<I, O>
{
	//~ Static fields/initializers ---------------------------------------------

	/** Internal signal for the first operation after a connect. */
	static final int FIRST_OPERATION = -2;

	/**
	 * State: the {@link AsynchronousChannelGroup} to associate any new
	 * asynchronous channels with.
	 */
	public static final RelationType<AsynchronousChannelGroup> CHANNEL_GROUP =
		newType();

	static
	{
		RelationTypes.init(AsynchronousSocketStep.class);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Returns the {@link AsynchronousChannelGroup} for asynchronous channel
	 * operations in the current scope. If no such group exists a new one will
	 * be created with the {@link ExecutorService} of the {@link
	 * CoroutineContext} and stored as {@link #CHANNEL_GROUP} in the current
	 * scope.
	 *
	 * @param  rContinuation The channel group
	 *
	 * @return The channel group
	 */
	protected AsynchronousChannelGroup getChannelGroup(
		Continuation<?> rContinuation)
	{
		AsynchronousChannelGroup rChannelGroup =
			rContinuation.getState(CHANNEL_GROUP);

		if (rChannelGroup == null)
		{
			Executor rContextExecutor = rContinuation.context().getExecutor();

			if (rContextExecutor instanceof ExecutorService)
			{
				try
				{
					rChannelGroup =
						AsynchronousChannelGroup.withThreadPool(
							(ExecutorService) rContextExecutor);
				}
				catch (IOException e)
				{
					throw new CoroutineException(e);
				}

				rContinuation.scope().set(CHANNEL_GROUP, rChannelGroup);
			}
		}

		return rChannelGroup;
	}

	//~ Inner Interfaces -------------------------------------------------------

	/********************************************************************
	 * A functional interface used as argument to {@link ChannelCallback}.
	 *
	 * @author eso
	 */
	@FunctionalInterface
	protected interface ChannelOperation<C extends AsynchronousChannel>
	{
		//~ Methods ------------------------------------------------------------

		/***************************************
		 * Performs an asnychronous channel operation if necessary or returns
		 * FALSE.
		 *
		 * @param  nBytesProcessed The number of bytes that have been processed
		 *                         by a previous invocation
		 * @param  rChannel        The channel to perform the operation on
		 * @param  rData           The byte buffer for the operation data
		 * @param  rCallback       The callback to be invoked (recursively) upon
		 *                         completion of the operation
		 *
		 * @return FALSE if a recursive asynchronous execution has been started,
		 *         TRUE if the operation is complete
		 *
		 * @throws Exception Any kind of exception may be thrown
		 */
		public boolean execute(int						   nBytesProcessed,
							   C						   rChannel,
							   ByteBuffer				   rData,
							   ChannelCallback<Integer, C> rCallback)
			throws Exception;
	}

	//~ Inner Classes ----------------------------------------------------------

	/********************************************************************
	 * A {@link CompletionHandler} implementation that performs an asynchronous
	 * channel operation and resumes a coroutine step afterwards
	 * (asynchronously).
	 *
	 * @author eso
	 */
	protected static class ChannelCallback<V, C extends AsynchronousChannel>
		implements CompletionHandler<V, ByteBuffer>
	{
		//~ Instance fields ----------------------------------------------------

		private final C						 rChannel;
		private final Suspension<ByteBuffer> rSuspension;
		private ChannelOperation<C>			 fOperation;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance.
		 *
		 * @param rChannel    The channel to operate on
		 * @param rSuspension The suspension to be resumed when the operation is
		 *                    completed
		 * @param fOperation  The asychronous channel operation to perform
		 */
		protected ChannelCallback(C						 rChannel,
								  Suspension<ByteBuffer> rSuspension,
								  ChannelOperation<C>    fOperation)
		{
			this.rChannel    = rChannel;
			this.rSuspension = rSuspension;
			this.fOperation  = fOperation;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public void completed(V rResult, ByteBuffer rData)
		{
			// first invocation from connect is Void, then read/write integers
			int nProcessed =
				rResult instanceof Integer ? ((Integer) rResult).intValue()
										   : FIRST_OPERATION;

			try
			{
				// required to force THIS to integer to have only one implementation
				// as the Java NIO API declares the connect stage callback as Void
				if (fOperation.execute(
						nProcessed,
						rChannel,
						rData,
						(ChannelCallback<Integer, C>) this))
				{
					rSuspension.resume(rData);
				}
			}
			catch (Exception e)
			{
				rSuspension.fail(e);
			}
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public void failed(Throwable eError, ByteBuffer rData)
		{
			rSuspension.fail(eError);
		}
	}
}
