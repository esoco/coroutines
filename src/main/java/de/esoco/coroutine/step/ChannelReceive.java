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
 * A coroutine step that receives a value from a channel. If the channel is
 * empty at the time of this step's invocation the coroutine execution will be
 * suspended until channel data becomes available. The channel to receive from
 * will be queried (and if not existing created) with {@link
 * CoroutineScope#getChannel(ChannelId)}.
 *
 * @author eso
 */
public class ChannelReceive<T> extends ChannelStep<Void, T>
{
	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance that receives from a certain channel.
	 *
	 * @param rId The ID of the channel
	 */
	public ChannelReceive(ChannelId<T> rId)
	{
		super(rId);
	}

	/***************************************
	 * Creates a new instance that receives from a channel the ID of which is
	 * provided in a state relation.
	 *
	 * @param rChannelType rThe type of state relation the source channel ID is
	 *                     stored in
	 */
	public ChannelReceive(RelationType<ChannelId<T>> rChannelType)
	{
		super(rChannelType);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends until a value can be received from a certain channel.
	 *
	 * @param  rId The ID of the channel to receive from
	 *
	 * @return A new instance of this class
	 */
	public static <T> ChannelReceive<T> receive(ChannelId<T> rId)
	{
		return new ChannelReceive<>(rId);
	}

	/***************************************
	 * Suspends until a value can be received from the channel with the ID
	 * stored in the relation with the given type.
	 *
	 * @param  rChannelType rThe type of the state relation the source channel
	 *                      ID is stored in
	 *
	 * @return A new step instance
	 */
	public static <T> ChannelReceive<T> receive(
		RelationType<ChannelId<T>> rChannelType)
	{
		return new ChannelReceive<>(rChannelType);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<Void> fPreviousExecution,
						 CoroutineStep<T, ?>	 rNextStep,
						 Continuation<?>		 rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
				  			v ->
				  				getChannel(rContinuation).receiveSuspending(
				  					rContinuation.suspend(rNextStep)),
				  			rContinuation)
						  .exceptionally(t ->
				  				rContinuation.fail(t));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected T execute(Void rIgnored, Continuation<?> rContinuation)
	{
		return getChannel(rContinuation).receiveBlocking();
	}
}
