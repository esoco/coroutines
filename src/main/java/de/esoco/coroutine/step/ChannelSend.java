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
package de.esoco.coroutine.step;

import de.esoco.coroutine.ChannelId;
import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.CoroutineScope;
import de.esoco.coroutine.CoroutineStep;

import java.util.concurrent.CompletableFuture;

import org.obrel.core.RelationType;


/********************************************************************
 * A coroutine step that sends a value into a channel. If the channel capacity
 * has been reached at the time of this step's invocation the coroutine
 * execution will be suspended until channel capacity becomes available. The
 * channel to send to will be queried (and if not existing created) by calling
 * {@link CoroutineScope#getChannel(ChannelId)}.
 *
 * <p>A send step returns the input value it sends so that it ca be processed
 * further in subsequent steps if needed.</p>
 *
 * @author eso
 */
public class ChannelSend<T> extends ChannelStep<T, T>
{
	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that sends to a certain channel.
	 *
	 * @param rId The ID of the channel
	 */
	public ChannelSend(ChannelId<T> rId)
	{
		super(rId);
	}

	/***************************************
	 * Creates a new instance that sends to a channel the ID of which is
	 * provided in a state relation.
	 *
	 * @param rChannelType rThe type of the state relation the target channel ID
	 *                     is stored in
	 */
	public ChannelSend(RelationType<ChannelId<T>> rChannelType)
	{
		super(rChannelType);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until a value can be sent to a certain channel.
	 *
	 * @param  rId The ID of the channel to send to
	 *
	 * @return A new step instance
	 */
	public static <T> ChannelSend<T> send(ChannelId<T> rId)
	{
		return new ChannelSend<>(rId);
	}

	/***************************************
	 * Suspends until a value can be sent to the channel with the ID stored in
	 * the relation with the given type.
	 *
	 * @param  rChannelType rThe type of the state relation the target channel
	 *                      ID is stored in
	 *
	 * @return A new step instance
	 */
	public static <T> ChannelSend<T> send(
		RelationType<ChannelId<T>> rChannelType)
	{
		return new ChannelSend<>(rChannelType);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<T> fPreviousExecution,
						 CoroutineStep<T, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
				  			v ->
				  				getChannel(rContinuation).sendSuspending(
				  					rContinuation.suspend(rNextStep, v)),
				  			rContinuation)
						  .exceptionally(t ->
				  				rContinuation.fail(t));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected T execute(T rInput, Continuation<?> rContinuation)
	{
		getChannel(rContinuation).sendBlocking(rInput);

		return rInput;
	}
}
